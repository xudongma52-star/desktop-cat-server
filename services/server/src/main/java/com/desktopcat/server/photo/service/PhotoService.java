package com.desktopcat.server.photo.service;

import com.desktopcat.server.photo.dto.PhotoContentDto;
import com.desktopcat.server.photo.dto.PhotoDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface PhotoService {

    PhotoDto upload(String username, MultipartFile file);

    List<PhotoDto> list(String username);

    PhotoContentDto getContent(String username, Long photoId);

    void delete(String username, Long photoId);
}
