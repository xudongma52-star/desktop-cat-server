package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.desktopcat.server.knowledge.client.RagServiceClient;
import com.desktopcat.server.knowledge.dto.KnowledgeRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.RagMatchDto;
import com.desktopcat.server.knowledge.dto.RagRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.RagRetrieveResponseDto;
import com.desktopcat.server.knowledge.service.KnowledgeRetrievalService;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordListItemDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class KnowledgeRetrievalServiceTest {
    private final PersonalRecordService personalRecordService = mock(PersonalRecordService.class);
    private final RagServiceClient ragServiceClient = mock(RagServiceClient.class);
    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrievalService(personalRecordService, ragServiceClient);
    }

    @Test
    void sendsOnlyRagEnabledRecordsAndMapsTrustedSourceMetadata() {
        PersonalRecordListItemDto enabled = listItem(1L, true);
        PersonalRecordListItemDto disabled = listItem(2L, false);
        PersonalRecordDetailDto detail = detail(1L, true, "晚上散步以后，我轻松了许多。");
        when(personalRecordService.listRecords(7L, 1, 100, null))
                .thenReturn(new PersonalRecordPageDto(List.of(enabled, disabled), 1, 100, 2, 1));
        when(personalRecordService.getRecord(7L, 1L)).thenReturn(detail);
        when(ragServiceClient.retrieve(any())).thenReturn(
                new RagRetrieveResponseDto(List.of(
                        new RagMatchDto(1L, "散步以后，我轻松了许多。", 0.72),
                        new RagMatchDto(999L, "不可信来源", 0.99))));

        var result = service.retrieve(7L, new KnowledgeRetrieveRequestDto(" 如何放松？ "));

        assertThat(result.searchableRecordCount()).isEqualTo(1);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().getFirst().recordId()).isEqualTo(1L);
        assertThat(result.matches().getFirst().recordType()).isEqualTo("DIARY");
        assertThat(result.matches().getFirst().score()).isEqualTo(0.72);

        ArgumentCaptor<RagRetrieveRequestDto> requestCaptor =
                ArgumentCaptor.forClass(RagRetrieveRequestDto.class);
        verify(ragServiceClient).retrieve(requestCaptor.capture());
        assertThat(requestCaptor.getValue().question()).isEqualTo("如何放松？");
        assertThat(requestCaptor.getValue().documents()).hasSize(1);
        assertThat(requestCaptor.getValue().documents().getFirst().documentId()).isEqualTo(1L);
        verify(personalRecordService, never()).getRecord(7L, 2L);
    }

    @Test
    void returnsEmptyResultWithoutCallingPythonWhenNoRecordIsEnabled() {
        when(personalRecordService.listRecords(7L, 1, 100, null))
                .thenReturn(new PersonalRecordPageDto(List.of(listItem(2L, false)), 1, 100, 1, 1));

        var result = service.retrieve(7L, new KnowledgeRetrieveRequestDto("散步"));

        assertThat(result.searchableRecordCount()).isZero();
        assertThat(result.matches()).isEmpty();
        verify(ragServiceClient, never()).retrieve(any());
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> service.retrieve(7L, new KnowledgeRetrieveRequestDto("  ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Question is required");
    }

    private PersonalRecordListItemDto listItem(long id, boolean ragEnabled) {
        return new PersonalRecordListItemDto(
                id, "DIARY", "一天", "摘要", LocalDate.of(2026, 9, 22), null,
                false, ragEnabled, 0, Instant.EPOCH, Instant.EPOCH);
    }

    private PersonalRecordDetailDto detail(long id, boolean ragEnabled, String content) {
        return new PersonalRecordDetailDto(
                id, "DIARY", "一天", content, LocalDate.of(2026, 9, 22), null,
                false, ragEnabled, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
