package com.desktopcat.server.capture.service;

import com.desktopcat.server.capture.dto.CaptureArticleCreateDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftRequestDto;
import com.desktopcat.server.capture.dto.CaptureClassificationResultDto;
import com.desktopcat.server.capture.dto.CaptureDailyPageDto;
import com.desktopcat.server.capture.dto.CaptureDirectRecordDto;
import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import java.time.LocalDate;
import java.util.List;

public interface CaptureOrganizationService {
    CaptureDailyPageDto listDaily(long userId, Integer page);
    CaptureClassificationResultDto classifyDay(long userId, LocalDate date);
    List<CaptureItemDto> listEmotions(long userId, LocalDate date);
    PersonalRecordDetailDto saveDirectRecord(
            long userId, String captureId, CaptureDirectRecordDto request);
    CaptureArticleDraftDto generateArticleDraft(
            long userId, CaptureArticleDraftRequestDto request);
    PersonalRecordDetailDto saveArticle(long userId, CaptureArticleCreateDto request);
    List<CaptureItemDto> listRecordSources(long userId, long recordId);
}
