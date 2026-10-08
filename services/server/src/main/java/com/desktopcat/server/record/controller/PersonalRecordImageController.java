package com.desktopcat.server.record.controller;

import com.desktopcat.server.identity.application.CurrentUserService;
import com.desktopcat.server.record.dto.PersonalRecordImageDto;
import com.desktopcat.server.record.service.RecordImageStorageService;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 配图上传与读取均按当前登录用户归属校验。 */
@RestController
@Profile("postgres")
@RequestMapping("/api/records/images")
public class PersonalRecordImageController {
    private final RecordImageStorageService storage;
    private final CurrentUserService users;

    public PersonalRecordImageController(RecordImageStorageService storage, CurrentUserService users) {
        this.storage = storage;
        this.users = users;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalRecordImageDto upload(@RequestPart(name = "file", required = false) MultipartFile file,
            Principal principal) {
        return storage.store(userId(principal), file);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<FileSystemResource> content(@PathVariable String filename, Principal principal)
            throws java.io.IOException {
        String key = "record-images/" + userId(principal) + "/" + filename;
        var path = storage.requireOwned(userId(principal), key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(storage.contentType(key)))
                .contentLength(java.nio.file.Files.size(path))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }

    private long userId(Principal principal) {
        return users.requireUserId(principal == null ? null : principal.getName());
    }
}
