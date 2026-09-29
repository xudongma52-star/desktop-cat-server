package com.desktopcat.server.capture.service;

import com.desktopcat.server.capture.dto.CaptureItemCreateDto;
import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.capture.dto.CaptureItemPageDto;
import com.desktopcat.server.capture.dto.CaptureItemUpdateDto;
import com.desktopcat.server.capture.dto.CaptureImageContentDto;
import org.springframework.web.multipart.MultipartFile;

public interface CaptureItemService {
    CaptureItemDto create(long userId, CaptureItemCreateDto request);
    CaptureItemDto createWithImage(long userId, CaptureItemCreateDto request, MultipartFile image);
    CaptureItemPageDto list(long userId, Integer page, Integer pageSize, String query);
    CaptureItemDto get(long userId, String captureId);
    CaptureImageContentDto getImage(long userId, String captureId);
    CaptureItemDto update(long userId, String captureId, CaptureItemUpdateDto request);
    void delete(long userId, String captureId, Integer version);
}
