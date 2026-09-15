package com.desktopcat.server.catprofile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatProfileService {
    private static final Logger log = LoggerFactory.getLogger(CatProfileService.class);
    private static final int MAX_CAT_NAME_LENGTH = 20;

    private final CatProfileRepository repository;

    public CatProfileService(CatProfileRepository repository) {
        this.repository = repository;
    }

    public CatProfileResponse getPrimaryProfile() {
        return CatProfileResponse.from(findPrimaryProfile());
    }

    public CatProfileResponse updatePrimaryName(UpdateCatNameRequest request) {
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

        String catName = request.catName().trim();
        if (catName.codePointCount(0, catName.length()) > MAX_CAT_NAME_LENGTH) {
            throw badRequest("Cat name must not exceed 20 characters.");
        }
        if (request.version() == null) {
            throw badRequest("Profile version is required.");
        }
        if (request.version() < 0) {
            throw badRequest("Profile version must not be negative.");
        }

        CatProfile current = findPrimaryProfile();
        if (current.profileId() != request.profileId()) {
            log.warn("event=cat_profile_not_found profileId={}", request.profileId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat profile was not found.");
        }

        int updatedRows = repository.updatePrimaryName(current.profileId(), catName, request.version());
        if (updatedRows == 0) {
            CatProfile latest = findPrimaryProfile();
            log.warn("event=cat_profile_update_conflict profileId={} requestedVersion={} currentVersion={}",
                    current.profileId(), request.version(), latest.version());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cat profile has been updated. Refresh and try again.");
        }

        CatProfile updated = findPrimaryProfile();
        log.info("event=cat_profile_name_updated profileId={} oldNameLength={} newNameLength={} "
                        + "versionBefore={} versionAfter={}",
                updated.profileId(), codePointLength(current.catName()), codePointLength(updated.catName()),
                current.version(), updated.version());
        return CatProfileResponse.from(updated);
    }

    private CatProfile findPrimaryProfile() {
        return repository.findPrimaryProfile().orElseThrow(() -> {
            log.warn("event=cat_profile_not_found profileKey=primary");
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat profile was not found.");
        });
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
