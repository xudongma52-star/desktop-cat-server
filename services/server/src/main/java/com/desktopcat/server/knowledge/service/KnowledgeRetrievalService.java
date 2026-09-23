package com.desktopcat.server.knowledge.service;

import com.desktopcat.server.knowledge.client.RagServiceClient;
import com.desktopcat.server.knowledge.dto.KnowledgeMatchDto;
import com.desktopcat.server.knowledge.dto.KnowledgeRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeSearchResultDto;
import com.desktopcat.server.knowledge.dto.RagDocumentDto;
import com.desktopcat.server.knowledge.dto.RagMatchDto;
import com.desktopcat.server.knowledge.dto.RagRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.RagRetrieveResponseDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordListItemDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 编排当前用户的知识记录和 Python 检索服务。 */
@Service
@Profile("postgres")
public class KnowledgeRetrievalService {
    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_CANDIDATE_RECORDS = 100;
    private static final int MAX_CONTENT_CODE_POINTS = 30_000;
    private static final int TOP_K = 5;

    private final PersonalRecordService personalRecordService;
    private final RagServiceClient ragServiceClient;

    public KnowledgeRetrievalService(
            PersonalRecordService personalRecordService,
            RagServiceClient ragServiceClient) {
        this.personalRecordService = personalRecordService;
        this.ragServiceClient = ragServiceClient;
    }

    public KnowledgeSearchResultDto retrieve(
            long userId, KnowledgeRetrieveRequestDto request) {
        String question = validateQuestion(request);
        PersonalRecordPageDto page = personalRecordService.listRecords(
                userId, 1, MAX_CANDIDATE_RECORDS, null);

        Map<Long, PersonalRecordDetailDto> sourceRecords = new LinkedHashMap<>();
        for (PersonalRecordListItemDto item : page.items()) {
            if (!item.ragEnabled()) {
                continue;
            }
            PersonalRecordDetailDto detail = personalRecordService.getRecord(userId, item.recordId());
            // 编辑和检索并发时以详情接口读到的最新开关为准。
            if (detail.ragEnabled()) {
                sourceRecords.put(detail.recordId(), detail);
            }
        }

        boolean candidateLimitReached = page.total() > MAX_CANDIDATE_RECORDS;
        if (sourceRecords.isEmpty()) {
            return new KnowledgeSearchResultDto(
                    0, candidateLimitReached, null, false, List.of());
        }

        List<RagDocumentDto> documents = sourceRecords.values().stream()
                .map(record -> new RagDocumentDto(
                        record.recordId(),
                        record.title(),
                        truncateContent(record.content()),
                        record.version()))
                .toList();
        RagRetrieveResponseDto response = ragServiceClient.retrieve(
                new RagRetrieveRequestDto(userId, question, TOP_K, documents));

        List<KnowledgeMatchDto> matches = response.matches().stream()
                .map(match -> toKnowledgeMatch(match, sourceRecords))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new KnowledgeSearchResultDto(
                sourceRecords.size(),
                candidateLimitReached,
                normalizeAnswer(response.answer(), response.answerGenerated(), matches),
                response.answerGenerated()
                        && response.answer() != null
                        && !response.answer().isBlank()
                        && !matches.isEmpty(),
                List.copyOf(matches));
    }

    private String validateQuestion(KnowledgeRetrieveRequestDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        if (request.question() == null || request.question().trim().isEmpty()) {
            throw badRequest("Question is required.");
        }
        String question = request.question().trim();
        if (question.codePointCount(0, question.length()) > MAX_QUESTION_LENGTH) {
            throw badRequest("Question must not exceed 500 characters.");
        }
        return question;
    }

    private String truncateContent(String content) {
        if (content.codePointCount(0, content.length()) <= MAX_CONTENT_CODE_POINTS) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, MAX_CONTENT_CODE_POINTS));
    }

    private KnowledgeMatchDto toKnowledgeMatch(
            RagMatchDto match, Map<Long, PersonalRecordDetailDto> sourceRecords) {
        PersonalRecordDetailDto source = sourceRecords.get(match.documentId());
        if (source == null || match.content() == null || match.content().isBlank()) {
            return null;
        }
        double normalizedScore = Math.max(0, Math.min(1, match.score()));
        return new KnowledgeMatchDto(
                source.recordId(),
                source.recordType(),
                source.title(),
                match.content(),
                source.recordDate(),
                normalizedScore);
    }

    private String normalizeAnswer(
            String answer, boolean answerGenerated, List<KnowledgeMatchDto> matches) {
        if (!answerGenerated || answer == null || answer.isBlank() || matches.isEmpty()) {
            return null;
        }
        return answer.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
