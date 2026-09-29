package com.desktopcat.server.capture.service.impl;

import com.desktopcat.server.capture.dao.CaptureItemDO;
import com.desktopcat.server.capture.dao.CaptureItemDao;
import com.desktopcat.server.capture.dto.CaptureItemCreateDto;
import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.capture.dto.CaptureItemPageDto;
import com.desktopcat.server.capture.dto.CaptureItemUpdateDto;
import com.desktopcat.server.capture.dto.CaptureImageContentDto;
import com.desktopcat.server.capture.service.CaptureImageStorageService;
import com.desktopcat.server.capture.service.CaptureItemService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 保存原始文字，客户端 UUID 使断网重试不会生成重复记录。 */
@Service
@Profile("postgres")
public class CaptureItemServiceImpl implements CaptureItemService {
    private static final Logger log = LoggerFactory.getLogger(CaptureItemServiceImpl.class);
    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 50;
    private final CaptureItemDao captureItemDao;
    private final CaptureImageStorageService imageStorageService;

    public CaptureItemServiceImpl(
            CaptureItemDao captureItemDao, CaptureImageStorageService imageStorageService) {
        this.captureItemDao = captureItemDao;
        this.imageStorageService = imageStorageService;
    }

    @Override
    @Transactional
    public CaptureItemDto create(long userId, CaptureItemCreateDto request) {
        if (request == null) throw badRequest("Request body is required.");
        String captureId = validId(request.captureId());
        String content = validContent(request.content());
        if (request.capturedAt() == null) throw badRequest("Capture time is required.");
        Instant capturedAt = request.capturedAt().truncatedTo(ChronoUnit.MILLIS);

        CaptureItemDO item = new CaptureItemDO();
        item.setCaptureId(captureId);
        item.setUserId(userId);
        item.setContent(content);
        item.setCapturedAt(capturedAt);
        int inserted = captureItemDao.insert(item);
        CaptureItemDO stored = captureItemDao.selectById(userId, captureId);
        if (stored == null || stored.getDeletedAt() != null
                || !content.equals(stored.getContent())
                || stored.getImageStorageKey() != null
                || !capturedAt.equals(stored.getCapturedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture id has already been used.");
        }
        if (inserted == 1) {
            log.info("event=capture_created captureId={} userId={} contentLength={}",
                    captureId, userId, content.codePointCount(0, content.length()));
        }
        return toDto(stored);
    }

    @Override
    @Transactional
    public CaptureItemDto createWithImage(
            long userId, CaptureItemCreateDto request, MultipartFile image) {
        if (request == null) throw badRequest("Request body is required.");
        String captureId = validId(request.captureId());
        String content = validImageContent(request.content());
        if (request.capturedAt() == null) throw badRequest("Capture time is required.");
        Instant capturedAt = request.capturedAt().truncatedTo(ChronoUnit.MILLIS);
        CaptureImageStorageService.StoredImage storedImage = imageStorageService.store(
                userId, captureId, image);
        if (storedImage.createdNew()) deleteImageIfTransactionRollsBack(storedImage.storageKey());

        CaptureItemDO item = new CaptureItemDO();
        item.setCaptureId(captureId);
        item.setUserId(userId);
        item.setContent(content);
        item.setImageStorageKey(storedImage.storageKey());
        item.setCapturedAt(capturedAt);
        int inserted = captureItemDao.insert(item);
        CaptureItemDO stored = captureItemDao.selectById(userId, captureId);
        if (stored == null || stored.getDeletedAt() != null
                || !content.equals(stored.getContent())
                || !capturedAt.equals(stored.getCapturedAt())
                || !storedImage.storageKey().equals(stored.getImageStorageKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture id has already been used.");
        }
        if (inserted == 1) {
            log.info("event=capture_created captureId={} userId={} contentLength={} hasImage=true",
                    captureId, userId, content.codePointCount(0, content.length()));
        }
        return toDto(stored);
    }

    @Override
    public CaptureItemPageDto list(long userId, Integer page, Integer pageSize, String query) {
        int normalizedPage = page == null ? 1 : page;
        int normalizedSize = pageSize == null ? 20 : pageSize;
        if (normalizedPage < 1) throw badRequest("Page must be at least 1.");
        if (normalizedSize < 1 || normalizedSize > MAX_PAGE_SIZE) {
            throw badRequest("Page size must be between 1 and 50.");
        }
        long offset = (long) (normalizedPage - 1) * normalizedSize;
        if (offset > Integer.MAX_VALUE) throw badRequest("Requested page is too large.");
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        if (normalizedQuery != null
                && normalizedQuery.codePointCount(0, normalizedQuery.length()) > MAX_QUERY_LENGTH) {
            throw badRequest("Search query must not exceed 100 characters.");
        }
        List<CaptureItemDto> items = captureItemDao.selectActivePage(
                userId, normalizedQuery, (int) offset, normalizedSize).stream()
                .map(this::toDto).toList();
        long total = captureItemDao.countActive(userId, normalizedQuery);
        long totalPages = (total + normalizedSize - 1) / normalizedSize;
        return new CaptureItemPageDto(items, normalizedPage, normalizedSize, total, totalPages);
    }

    @Override
    public CaptureItemDto get(long userId, String captureId) {
        return toDto(requireActive(userId, validId(captureId)));
    }

    @Override
    public CaptureImageContentDto getImage(long userId, String captureId) {
        CaptureItemDO item = requireActive(userId, validId(captureId));
        if (item.getImageStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture image was not found.");
        }
        Path path = imageStorageService.requireExisting(item.getImageStorageKey());
        try {
            return new CaptureImageContentDto(path,
                    imageStorageService.contentType(item.getImageStorageKey()), Files.size(path));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Capture image file is unavailable.", exception);
        }
    }

    @Override
    @Transactional
    public CaptureItemDto update(long userId, String captureId, CaptureItemUpdateDto request) {
        String normalizedId = validId(captureId);
        if (request == null) throw badRequest("Request body is required.");
        CaptureItemDO current = requireActive(userId, normalizedId);
        String content = current.getImageStorageKey() == null
                ? validContent(request.content()) : validImageContent(request.content());
        int version = validVersion(request.version());
        if (captureItemDao.updateContent(userId, normalizedId, content, version) != 1) {
            requireActive(userId, normalizedId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture has been updated. Refresh and try again.");
        }
        log.info("event=capture_updated captureId={} userId={} versionBefore={}",
                normalizedId, userId, version);
        return toDto(requireActive(userId, normalizedId));
    }

    @Override
    @Transactional
    public void delete(long userId, String captureId, Integer version) {
        String normalizedId = validId(captureId);
        int normalizedVersion = validVersion(version);
        CaptureItemDO current = requireActive(userId, normalizedId);
        if (captureItemDao.logicalDelete(userId, normalizedId, normalizedVersion) != 1) {
            requireActive(userId, normalizedId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture has been updated. Refresh and try again.");
        }
        if (current.getImageStorageKey() != null) deleteImageAfterTransactionCommits(
                current.getImageStorageKey());
        log.info("event=capture_deleted captureId={} userId={}", normalizedId, userId);
    }

    private CaptureItemDO requireActive(long userId, String captureId) {
        CaptureItemDO item = captureItemDao.selectById(userId, captureId);
        if (item == null || item.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture was not found.");
        }
        return item;
    }

    private String validId(String value) {
        if (value == null || value.isBlank()) throw badRequest("Capture id is required.");
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw badRequest("Capture id is invalid.");
        }
    }

    private String validContent(String value) {
        if (value == null || value.isBlank()) throw badRequest("Capture content is required.");
        if (value.codePointCount(0, value.length()) > MAX_CONTENT_LENGTH) {
            throw badRequest("Capture content must not exceed 20000 characters.");
        }
        return value;
    }

    private String validImageContent(String value) {
        String content = value == null ? "" : value;
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw badRequest("Capture content must not exceed 20000 characters.");
        }
        return content;
    }

    private int validVersion(Integer value) {
        if (value == null) throw badRequest("Capture version is required.");
        if (value < 0) throw badRequest("Capture version must not be negative.");
        return value;
    }

    private CaptureItemDto toDto(CaptureItemDO item) {
        String imageUrl = item.getImageStorageKey() == null ? null
                : "/api/captures/%s/image".formatted(item.getCaptureId());
        return new CaptureItemDto(item.getCaptureId(), item.getContent(), imageUrl, item.getCapturedAt(),
                item.getVersion(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private void deleteImageIfTransactionRollsBack(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    imageStorageService.deleteQuietly(storageKey);
                }
            }
        });
    }

    private void deleteImageAfterTransactionCommits(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorageService.deleteQuietly(storageKey);
            }
        });
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
