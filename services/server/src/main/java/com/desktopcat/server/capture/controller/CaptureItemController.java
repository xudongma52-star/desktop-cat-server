package com.desktopcat.server.capture.controller;

import com.desktopcat.server.capture.dto.CaptureItemCreateDto;
import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.capture.dto.CaptureItemPageDto;
import com.desktopcat.server.capture.dto.CaptureItemUpdateDto;
import com.desktopcat.server.capture.dto.CaptureImageContentDto;
import com.desktopcat.server.capture.dto.CaptureArticleCreateDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftDto;
import com.desktopcat.server.capture.dto.CaptureArticleDraftRequestDto;
import com.desktopcat.server.capture.dto.CaptureClassifyDayDto;
import com.desktopcat.server.capture.dto.CaptureClassificationResultDto;
import com.desktopcat.server.capture.dto.CaptureDailyPageDto;
import com.desktopcat.server.capture.dto.CaptureDirectRecordDto;
import com.desktopcat.server.capture.service.CaptureItemService;
import com.desktopcat.server.capture.service.CaptureOrganizationService;
import com.desktopcat.server.identity.application.CurrentUserService;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("postgres")
@RequestMapping("/api/captures")
public class CaptureItemController {
    private final CaptureItemService captureItemService;
    private final CaptureOrganizationService captureOrganizationService;
    private final CurrentUserService currentUserService;

    public CaptureItemController(
            CaptureItemService captureItemService,
            CaptureOrganizationService captureOrganizationService,
            CurrentUserService currentUserService) {
        this.captureItemService = captureItemService;
        this.captureOrganizationService = captureOrganizationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CaptureItemDto create(@RequestBody(required = false) CaptureItemCreateDto request,
            Principal principal) {
        return captureItemService.create(userId(principal), request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CaptureItemDto createWithImage(
            @RequestPart(name = "capture", required = false) CaptureItemCreateDto request,
            @RequestPart(name = "image", required = false) MultipartFile image,
            Principal principal) {
        return captureItemService.createWithImage(userId(principal), request, image);
    }

    @GetMapping
    public CaptureItemPageDto list(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String q,
            Principal principal) {
        return captureItemService.list(userId(principal), page, pageSize, q);
    }

    @GetMapping("/daily")
    public CaptureDailyPageDto listDaily(
            @RequestParam(required = false) Integer page, Principal principal) {
        return captureOrganizationService.listDaily(userId(principal), page);
    }

    @PostMapping("/classify-day")
    public CaptureClassificationResultDto classifyDay(
            @RequestBody(required = false) CaptureClassifyDayDto request, Principal principal) {
        return captureOrganizationService.classifyDay(
                userId(principal), request == null ? null : request.date());
    }

    @GetMapping("/emotions")
    public List<CaptureItemDto> listEmotions(
            @RequestParam(required = false) LocalDate date, Principal principal) {
        return captureOrganizationService.listEmotions(userId(principal), date);
    }

    @PostMapping("/{captureId}/record")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalRecordDetailDto saveDirectRecord(
            @PathVariable String captureId,
            @RequestBody(required = false) CaptureDirectRecordDto request,
            Principal principal) {
        return captureOrganizationService.saveDirectRecord(
                userId(principal), captureId, request);
    }

    @PostMapping("/article-draft")
    public CaptureArticleDraftDto generateArticleDraft(
            @RequestBody(required = false) CaptureArticleDraftRequestDto request,
            Principal principal) {
        return captureOrganizationService.generateArticleDraft(userId(principal), request);
    }

    @PostMapping("/article")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalRecordDetailDto saveArticle(
            @RequestBody(required = false) CaptureArticleCreateDto request,
            Principal principal) {
        return captureOrganizationService.saveArticle(userId(principal), request);
    }

    @GetMapping("/{captureId}")
    public CaptureItemDto get(@PathVariable String captureId, Principal principal) {
        return captureItemService.get(userId(principal), captureId);
    }

    @GetMapping("/{captureId}/image")
    public ResponseEntity<FileSystemResource> image(
            @PathVariable String captureId, Principal principal) {
        CaptureImageContentDto image = captureItemService.getImage(userId(principal), captureId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.contentLength())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(image.path()));
    }

    @PutMapping("/{captureId}")
    public CaptureItemDto update(@PathVariable String captureId,
            @RequestBody(required = false) CaptureItemUpdateDto request, Principal principal) {
        return captureItemService.update(userId(principal), captureId, request);
    }

    @DeleteMapping("/{captureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String captureId,
            @RequestParam(required = false) Integer version, Principal principal) {
        captureItemService.delete(userId(principal), captureId, version);
    }

    private long userId(Principal principal) {
        return currentUserService.requireUserId(principal == null ? null : principal.getName());
    }
}
