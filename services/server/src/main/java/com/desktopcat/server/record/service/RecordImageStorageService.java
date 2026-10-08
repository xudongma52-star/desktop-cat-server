package com.desktopcat.server.record.service;

import com.desktopcat.server.record.dto.PersonalRecordImageDto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 文章原图独立存储，流式写入，不继承照片轮播的尺寸、压缩和大小限制。 */
@Service
public class RecordImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(RecordImageStorageService.class);
    private final Path root;

    public RecordImageStorageService(@Value("${desktop-cat.photo.storage-root:./data}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public PersonalRecordImageDto store(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("Record image is required.");
        Path temporary = null;
        try {
            Path directory = root.resolve("record-images/" + userId);
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "upload-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            String extension = detectExtension(temporary);
            String key = "record-images/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
            Files.move(temporary, root.resolve(key));
            log.info("event=record_image_uploaded userId={} imageKey={} bytes={}", userId, key, file.getSize());
            return new PersonalRecordImageDto(key, imageUrl(key));
        } catch (IOException exception) {
            log.error("event=record_image_upload_failed userId={}", userId, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Record image could not be stored.", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { log.warn("event=record_image_temp_cleanup_failed userId={}", userId); }
            }
        }
    }

    public Path requireOwned(long userId, String key) {
        String prefix = "record-images/" + userId + "/";
        if (key == null || !key.startsWith(prefix)
                || !key.substring(prefix.length()).matches("[a-f0-9-]{36}\\.(png|jpg|webp)")) {
            throw badRequest("Record image does not belong to this user.");
        }
        Path path = root.resolve(key).normalize();
        if (!Files.isRegularFile(path)) throw badRequest("Record image is unavailable.");
        return path;
    }

    public static String imageUrl(String key) {
        return key == null ? null : "/api/records/images/" + key.substring(key.lastIndexOf('/') + 1);
    }

    public String contentType(String key) {
        return key.endsWith(".png") ? "image/png" : key.endsWith(".jpg") ? "image/jpeg" : "image/webp";
    }

    private String detectExtension(Path path) throws IOException {
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) { header = input.readNBytes(32); }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P' && Files.size(path) >= 30) return "webp";
        // 仅读取图片元数据确认格式，不解码整张原图，避免大图占满内存。
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badRequest("Record image must be JPG, PNG, or WebP.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) throw badRequest("Record image is invalid.");
                return switch (reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    default -> throw badRequest("Record image must be JPG, PNG, or WebP.");
                };
            } finally { reader.dispose(); }
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
