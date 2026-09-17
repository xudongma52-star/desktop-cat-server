package com.desktopcat.server.catprofile.service.impl;

import com.desktopcat.server.catprofile.dao.CatProfileDao;
import com.desktopcat.server.catprofile.dao.CatProfileDO;
import com.desktopcat.server.catprofile.dto.CatProfileDto;
import com.desktopcat.server.catprofile.dto.CatProfileUpdateDto;
import com.desktopcat.server.catprofile.service.CatProfileService;
import com.desktopcat.server.events.AssistantEventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("postgres")
public class CatProfileServiceImpl implements CatProfileService {
    //定义日志工具，提供log能力
    private static final Logger log = LoggerFactory.getLogger(CatProfileServiceImpl.class);
    //名字最大长度常量，小猫名称目前最大限制是20
    private static final int MAX_CAT_NAME_LENGTH = 20;

    private final CatProfileDao catProfileDao;
    //事件推送依赖
    private final AssistantEventStream eventStream;

    public CatProfileServiceImpl(CatProfileDao catProfileDao, AssistantEventStream eventStream) {
        this.catProfileDao = catProfileDao;
        this.eventStream = eventStream;
    }

    @Override
    public CatProfileDto getPrimaryProfile() {
        return toDto(findPrimaryProfile());
    }

    @Override
    public CatProfileDto updatePrimaryName(CatProfileUpdateDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        if (request.profileId() == null) {
            throw badRequest("Profile id is required.");
        }
        if (request.profileId() <= 0) {
            throw badRequest("Profile id must be positive.");
        }
        if (request.catName() == null || request.catName().trim().isEmpty()) {
            throw badRequest("Cat name is required.");
        }

        String normalizedCatName = request.catName().trim();
        if (normalizedCatName.codePointCount(0, normalizedCatName.length()) > MAX_CAT_NAME_LENGTH) {
            throw badRequest("Cat name must not exceed 20 characters.");
        }
        if (request.version() == null) {
            throw badRequest("Profile version is required.");
        }
        if (request.version() < 0) {
            throw badRequest("Profile version must not be negative.");
        }

        CatProfileDO current = findPrimaryProfile();
        if (current.profileId() != request.profileId()) {
            log.warn("event=cat_profile_not_found profileId={}", request.profileId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat profile was not found.");
        }

        int updatedRows = catProfileDao.updatePrimaryName(
                current.profileId(), normalizedCatName, request.version());
        if (updatedRows == 0) {
            CatProfileDO latest = findPrimaryProfile();
            log.warn("event=cat_profile_update_conflict profileId={} requestedVersion={} currentVersion={}",
                    current.profileId(), request.version(), latest.version());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cat profile has been updated. Refresh and try again.");
        }

        CatProfileDO updated = findPrimaryProfile();
        log.info("event=cat_profile_name_updated profileId={} oldNameLength={} newNameLength={} "
                        + "versionBefore={} versionAfter={}",
                updated.profileId(), codePointLength(current.catName()), codePointLength(updated.catName()),
                current.version(), updated.version());
        eventStream.publishCatProfileUpdated(updated.profileId(), updated.version());
        return toDto(updated);
    }

    private CatProfileDO findPrimaryProfile() {
        CatProfileDO profile = catProfileDao.selectPrimaryProfile();
        if (profile != null) return profile;
        log.warn("event=cat_profile_not_found profileKey=primary");
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat profile was not found.");
    }

    private CatProfileDto toDto(CatProfileDO profile) {
        return new CatProfileDto(
                profile.profileId(), profile.catName(), profile.version(), profile.updatedAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
