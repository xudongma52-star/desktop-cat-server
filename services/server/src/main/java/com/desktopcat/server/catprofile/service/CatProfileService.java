package com.desktopcat.server.catprofile.service;

import com.desktopcat.server.catprofile.dto.CatProfileDto;
import com.desktopcat.server.catprofile.dto.CatProfileUpdateDto;

public interface CatProfileService {
    //获取当前小猫资料
    CatProfileDto getPrimaryProfile();
    //修改主要小猫的名字，并返回修改后的完整资料
    CatProfileDto updatePrimaryName(CatProfileUpdateDto request);
}
