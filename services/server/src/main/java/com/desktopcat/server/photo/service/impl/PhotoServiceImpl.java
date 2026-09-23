package com.desktopcat.server.photo.service.impl;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import com.desktopcat.server.photo.dao.PhotoDO;
import com.desktopcat.server.photo.dao.PhotoDao;
import com.desktopcat.server.photo.dto.PhotoContentDto;
import com.desktopcat.server.photo.dto.PhotoDto;
import com.desktopcat.server.photo.service.PhotoService;
import com.desktopcat.server.photo.service.PhotoStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

@Service
@Profile("postgres")
public class PhotoServiceImpl implements PhotoService {
    private static final Logger log = LoggerFactory.getLogger(PhotoServiceImpl.class);

    private final PhotoDao photoDao;
    private final AppUserDao appUserDao;
    private final PhotoStorageService photoStorageService;

    public PhotoServiceImpl(
            PhotoDao photoDao, AppUserDao appUserDao, PhotoStorageService photoStorageService) {
        this.photoDao = photoDao;
        this.appUserDao = appUserDao;
        this.photoStorageService = photoStorageService;
    }

    @Override
    @Transactional
    public PhotoDto upload(String username, MultipartFile file) {
        AppUserDO user = requireUser(username);
        String storageKey = photoStorageService.store(user.userId(), file);
        deleteStoredFileIfTransactionRollsBack(storageKey);

        PhotoDO photo = new PhotoDO(null, user.userId(), storageKey, null, null);
        if (photoDao.insertPhoto(photo) != 1 || photo.getPhotoId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be created.");
        }

        PhotoDO created = photoDao.selectActiveByIdAndUserId(photo.getPhotoId(), user.userId());
        if (created == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be created.");
        }
        log.info("event=photo_uploaded userId={} photoId={}", user.userId(), created.getPhotoId());
        return toDto(created);
    }

    @Override
    public List<PhotoDto> list(String username) {
        AppUserDO user = requireUser(username);
        return photoDao.selectActiveByUserId(user.userId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public PhotoContentDto getContent(String username, Long photoId) {
        PhotoDO photo = requirePhoto(username, photoId);
        Path path = photoStorageService.requireExisting(photo.getStorageKey());
        try {
            return new PhotoContentDto(path, Files.size(path));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo file is unavailable.", exception);
        }
    }

    @Override
    @Transactional
    public void delete(String username, Long photoId) {
        AppUserDO user = requireUser(username);
        validatePhotoId(photoId);
        if (photoDao.logicalDelete(photoId, user.userId()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo was not found.");
        }
        log.info("event=photo_deleted userId={} photoId={}", user.userId(), photoId);
    }

    private PhotoDO requirePhoto(String username, Long photoId) {
        AppUserDO user = requireUser(username);
        validatePhotoId(photoId);
        PhotoDO photo = photoDao.selectActiveByIdAndUserId(photoId, user.userId());
        if (photo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo was not found.");
        }
        return photo;
    }

    private AppUserDO requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        AppUserDO user = appUserDao.selectByUsername(username);
        if (user == null || !"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is unavailable.");
        }
        return user;
    }

    private void validatePhotoId(Long photoId) {
        if (photoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo id is required.");
        }
        if (photoId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo id must be positive.");
        }
    }

    private PhotoDto toDto(PhotoDO photo) {
        return new PhotoDto(
                photo.getPhotoId(),
                "/api/carousel/photos/%d/content".formatted(photo.getPhotoId()),
                photo.getCreatedAt());
    }

    private void deleteStoredFileIfTransactionRollsBack(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    photoStorageService.deleteQuietly(storageKey);
                }
            }
        });
    }
}
