package com.desktopcat.server.catprofile.service;

import com.desktopcat.server.catprofile.dao.CatProfileDO;

public interface CatProfileService {
    //获取当前小猫资料
    CatProfileDO getPrimaryProfile();
    //修改主要小猫的名字，并返回修改后的完整资料
    CatProfileDO updatePrimaryName(Long profileId, String catName, Integer version);
}
