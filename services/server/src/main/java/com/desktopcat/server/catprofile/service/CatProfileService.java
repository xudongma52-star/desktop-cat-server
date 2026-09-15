package com.desktopcat.server.catprofile.service;

import com.desktopcat.server.catprofile.dao.CatProfileDO;

public interface CatProfileService {
    CatProfileDO getPrimaryProfile();

    CatProfileDO updatePrimaryName(Long profileId, String catName, Integer version);
}
