package com.desktopcat.server.catprofile;

import java.util.Optional;

public interface CatProfileRepository {
    Optional<CatProfile> findPrimaryProfile();

    int updatePrimaryName(long profileId, String catName, int version);
}
