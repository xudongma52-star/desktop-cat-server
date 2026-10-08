package com.desktopcat.server.capture.service.impl;

import com.desktopcat.server.capture.client.CaptureAiClient;
import com.desktopcat.server.capture.dao.CaptureItemDO;
import com.desktopcat.server.capture.dao.CaptureItemDao;
import com.desktopcat.server.capture.dto.CaptureAiArticleRequestDto;
import com.desktopcat.server.capture.dto.CaptureAiArticleResponseDto;
import com.desktopcat.server.capture.dto.CaptureAiClassificationRequestDto;
import com.desktopcat.server.capture.dto.CaptureAiClassificationResponseDto;
import com.desktopcat.server.capture.dto.CaptureAiDecisionDto;
import com.desktopcat.server.capture.dto.CaptureAiItemDto;
import com.desktopcat.server.capture.dto.CaptureArticleCreateDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftRequestDto;
import com.desktopcat.server.capture.dto.CaptureClassificationResultDto;
import com.desktopcat.server.capture.dto.CaptureDailyPageDto;
import com.desktopcat.server.capture.dto.CaptureDirectRecordDto;
import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.capture.service.CaptureImageStorageService;
import com.desktopcat.server.capture.service.CaptureOrganizationService;
import com.desktopcat.server.record.dao.PersonalRecordCaptureSourceDO;
import com.desktopcat.server.record.dao.PersonalRecordCaptureSourceDao;
import com.desktopcat.server.record.dto.PersonalRecordCreateDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 编排按天分类、记录生成和原始碎片溯源。 */
@Service
@Profile("postgres")
public class CaptureOrganizationServiceImpl implements CaptureOrganizationService {
    private static final Logger log = LoggerFactory.getLogger(CaptureOrganizationServiceImpl.class);
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> CLASSIFICATION_TARGETS = Set.of("RECORD", "EMOTION");
    private static final int MAX_ARTICLE_SOURCES = 30;

    private final CaptureItemDao captureItemDao;
    private final CaptureImageStorageService imageStorageService;
    private final CaptureAiClient captureAiClient;
    private final PersonalRecordService personalRecordService;
    private final PersonalRecordCaptureSourceDao sourceDao;

    public CaptureOrganizationServiceImpl(
            CaptureItemDao captureItemDao,
            CaptureImageStorageService imageStorageService,
            CaptureAiClient captureAiClient,
            PersonalRecordService personalRecordService,
            PersonalRecordCaptureSourceDao sourceDao) {
        this.captureItemDao = captureItemDao;
        this.imageStorageService = imageStorageService;
        this.captureAiClient = captureAiClient;
        this.personalRecordService = personalRecordService;
        this.sourceDao = sourceDao;
    }

    @Override
    public CaptureDailyPageDto listDaily(long userId, Integer page) {
        int normalizedPage = page == null ? 1 : page;
        if (normalizedPage < 1) throw badRequest("Page must be at least 1.");
        long totalDays = captureItemDao.countActiveDates(userId);
        if (totalDays == 0) {
            return new CaptureDailyPageDto(null, List.of(), 1, 0);
        }
        if (normalizedPage > totalDays) throw badRequest("Requested day page does not exist.");
        List<LocalDate> dates = captureItemDao.selectActiveDates(userId, normalizedPage - 1, 1);
        if (dates.isEmpty()) throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Capture day list changed. Refresh and try again.");
        LocalDate date = dates.getFirst();
        return new CaptureDailyPageDto(date,
                listByDate(userId, date, null, false), normalizedPage, totalDays);
    }

    @Override
    public CaptureClassificationResultDto classifyDay(long userId, LocalDate date) {
        LocalDate normalizedDate = requireDate(date);
        List<CaptureItemDO> pending = selectByDate(userId, normalizedDate, null, true);
        if (pending.isEmpty()) return new CaptureClassificationResultDto(0);

        CaptureAiClassificationResponseDto response = captureAiClient.classify(
                new CaptureAiClassificationRequestDto(toAiItems(pending)));
        Map<String, CaptureAiDecisionDto> decisions = validateDecisions(pending, response);
        int classified = 0;
        for (CaptureItemDO item : pending) {
            CaptureAiDecisionDto decision = decisions.get(item.getCaptureId());
            classified += captureItemDao.classifyIfUnclassified(
                    userId, item.getCaptureId(), decision.target(), "AI", item.getVersion());
        }
        log.info("event=capture_day_classified userId={} date={} pendingCount={} classifiedCount={}",
                userId, normalizedDate, pending.size(), classified);
        return new CaptureClassificationResultDto(classified);
    }

    @Override
    public List<CaptureItemDto> listEmotions(long userId, LocalDate date) {
        return listByDate(userId, requireDate(date), "EMOTION", false);
    }

    @Override
    @Transactional
    public PersonalRecordDetailDto saveDirectRecord(
            long userId, String captureId, CaptureDirectRecordDto request) {
        CaptureItemDO item = requirePendingRecord(userId, captureId);
        String content = item.getContent();
        if ((content == null || content.isBlank()) && request != null) {
            content = request.content();
        }
        if (content == null || content.isBlank()) {
            throw badRequest("Image-only capture needs a text note before direct save.");
        }
        if (content.length() > 10_000) throw badRequest("Record content is too long.");
        boolean ragEnabled = request != null && Boolean.TRUE.equals(request.ragEnabled());
        PersonalRecordDetailDto record = personalRecordService.createRecord(userId,
                new PersonalRecordCreateDto("NOTE", null, content.strip(),
                        localDate(item.getCapturedAt()), null, false, ragEnabled));
        linkAndResolve(userId, record.recordId(), List.of(item), "DIRECT");
        return record;
    }

    @Override
    public CaptureArticleDraftDto generateArticleDraft(
            long userId, CaptureArticleDraftRequestDto request) {
        List<CaptureItemDO> items = requirePendingRecords(userId,
                request == null ? null : request.captureIds());
        CaptureAiArticleResponseDto response = captureAiClient.draftArticle(
                new CaptureAiArticleRequestDto(toAiItems(items)));
        if (response.title() == null || response.title().isBlank()
                || response.content() == null || response.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI organization service returned an invalid article.");
        }
        LocalDate recordDate = items.stream().map(CaptureItemDO::getCapturedAt)
                .max(Instant::compareTo).map(this::localDate).orElse(LocalDate.now(USER_ZONE));
        return new CaptureArticleDraftDto(response.title().strip(), response.content().strip(),
                recordDate, items.stream().map(CaptureItemDO::getCaptureId).toList());
    }

    @Override
    @Transactional
    public PersonalRecordDetailDto saveArticle(long userId, CaptureArticleCreateDto request) {
        if (request == null) throw badRequest("Request body is required.");
        List<CaptureItemDO> items = requirePendingRecords(userId, request.captureIds());
        PersonalRecordDetailDto record = personalRecordService.createRecord(userId,
                new PersonalRecordCreateDto("NOTE", request.title(), request.content(),
                        request.recordDate(), null, false, Boolean.TRUE.equals(request.ragEnabled())));
        linkAndResolve(userId, record.recordId(), items, "AI_ARTICLE");
        return record;
    }

    @Override
    public List<CaptureItemDto> listRecordSources(long userId, long recordId) {
        personalRecordService.getRecord(userId, recordId);
        List<CaptureItemDto> result = new ArrayList<>();
        for (String captureId : sourceDao.selectCaptureIdsByRecordId(recordId)) {
            CaptureItemDO item = captureItemDao.selectById(userId, captureId);
            if (item != null && item.getDeletedAt() == null) result.add(toDto(item));
        }
        return List.copyOf(result);
    }

    private List<CaptureItemDto> listByDate(
            long userId, LocalDate date, String target, boolean unclassifiedOnly) {
        return selectByDate(userId, date, target, unclassifiedOnly).stream()
                .map(this::toDto).toList();
    }

    private List<CaptureItemDO> selectByDate(
            long userId, LocalDate date, String target, boolean unclassifiedOnly) {
        Instant start = date.atStartOfDay(USER_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(USER_ZONE).toInstant();
        return captureItemDao.selectActiveByTimeRange(
                userId, start, end, target, unclassifiedOnly);
    }

    private Map<String, CaptureAiDecisionDto> validateDecisions(
            List<CaptureItemDO> pending, CaptureAiClassificationResponseDto response) {
        if (response.decisions() == null) throw invalidClassification();
        Set<String> requested = pending.stream().map(CaptureItemDO::getCaptureId)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, CaptureAiDecisionDto> decisions = new HashMap<>();
        for (CaptureAiDecisionDto decision : response.decisions()) {
            if (decision == null || !requested.contains(decision.captureId())
                    || !CLASSIFICATION_TARGETS.contains(decision.target())
                    || decisions.putIfAbsent(decision.captureId(), decision) != null) {
                throw invalidClassification();
            }
        }
        if (!decisions.keySet().equals(requested)) throw invalidClassification();
        return decisions;
    }

    private ResponseStatusException invalidClassification() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI organization service returned invalid classifications.");
    }

    private List<CaptureItemDO> requirePendingRecords(long userId, List<String> captureIds) {
        if (captureIds == null || captureIds.isEmpty()) {
            throw badRequest("At least one capture is required.");
        }
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(captureIds);
        if (uniqueIds.size() != captureIds.size()) throw badRequest("Capture ids must be unique.");
        if (uniqueIds.size() > MAX_ARTICLE_SOURCES) {
            throw badRequest("An article can use at most 30 captures.");
        }
        return uniqueIds.stream().map(id -> requirePendingRecord(userId, id)).toList();
    }

    private CaptureItemDO requirePendingRecord(long userId, String captureId) {
        try {
            UUID.fromString(captureId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw badRequest("Capture id is invalid.");
        }
        CaptureItemDO item = captureItemDao.selectById(userId, captureId);
        if (item == null || item.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture was not found.");
        }
        if (!"RECORD".equals(item.getClassificationTarget())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture is not classified as a record.");
        }
        if (item.getRecordResolution() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture has already been added to a record.");
        }
        return item;
    }

    private void linkAndResolve(
            long userId, long recordId, List<CaptureItemDO> items, String resolution) {
        for (CaptureItemDO item : items) {
            if (sourceDao.insert(new PersonalRecordCaptureSourceDO(
                    recordId, item.getCaptureId())) != 1) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Record source could not be saved.");
            }
            if (captureItemDao.resolveRecord(userId, item.getCaptureId(),
                    resolution, item.getVersion()) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Capture has changed. Refresh and try again.");
            }
        }
    }

    private List<CaptureAiItemDto> toAiItems(List<CaptureItemDO> items) {
        return items.stream().map(this::toAiItem).toList();
    }

    private CaptureAiItemDto toAiItem(CaptureItemDO item) {
        String content = item.getContent() == null ? "" : item.getContent();
        if (item.getImageStorageKey() == null) {
            return new CaptureAiItemDto(item.getCaptureId(), content, null, null);
        }
        try {
            byte[] bytes = Files.readAllBytes(
                    imageStorageService.requireExisting(item.getImageStorageKey()));
            return new CaptureAiItemDto(item.getCaptureId(), content,
                    imageStorageService.contentType(item.getImageStorageKey()),
                    Base64.getEncoder().encodeToString(bytes));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Capture image file is unavailable.", exception);
        }
    }

    private CaptureItemDto toDto(CaptureItemDO item) {
        String imageUrl = item.getImageStorageKey() == null ? null
                : "/api/captures/%s/image".formatted(item.getCaptureId());
        return new CaptureItemDto(item.getCaptureId(), item.getContent(), imageUrl,
                item.getClassificationTarget(), item.getClassificationOrigin(),
                item.getRecordResolution(), item.getCapturedAt(), item.getVersion(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) throw badRequest("Capture date is required.");
        return date;
    }

    private LocalDate localDate(Instant instant) {
        return instant.atZone(USER_ZONE).toLocalDate();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
