package com.desktopcat.server.photo.controller;

import com.desktopcat.server.photo.dto.PhotoContentDto;
import com.desktopcat.server.photo.dto.PhotoDto;
import com.desktopcat.server.photo.service.PhotoService;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("postgres")
@RequestMapping("/api/carousel/photos")
public class PhotoController {
    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoDto upload(
            @RequestPart(name = "file", required = false) MultipartFile file, Principal principal) {
        return photoService.upload(principal == null ? null : principal.getName(), file);
    }

    @GetMapping
    public List<PhotoDto> list(Principal principal) {
        return photoService.list(principal == null ? null : principal.getName());
    }

    @GetMapping(value = "/{photoId}/content", produces = "image/webp")
    public ResponseEntity<FileSystemResource> content(
            @PathVariable Long photoId, Principal principal) {
        PhotoContentDto content = photoService.getContent(
                principal == null ? null : principal.getName(), photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .contentLength(content.contentLength())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(content.path()));
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long photoId, Principal principal) {
        photoService.delete(principal == null ? null : principal.getName(), photoId);
    }
}
