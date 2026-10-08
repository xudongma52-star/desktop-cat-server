package com.desktopcat.server.record;

import static org.junit.jupiter.api.Assertions.*;
import com.desktopcat.server.record.service.RecordImageStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class RecordImageStorageServiceTest {
    @TempDir Path directory;

    @Test
    void preservesLargeOriginalAndRejectsOtherUsersImage() throws Exception {
        var encoded = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB), "png", encoded);
        // 超过既有 11 MB 请求上限，原字节必须完整保留，而非压缩或裁剪。
        byte[] original = Arrays.copyOf(encoded.toByteArray(), 12 * 1024 * 1024);
        var storage = new RecordImageStorageService(directory.toString());
        var image = storage.store(5, new MockMultipartFile("file", "original.png", "image/png", original));
        assertArrayEquals(original, Files.readAllBytes(storage.requireOwned(5, image.imageKey())));
        assertThrows(ResponseStatusException.class, () -> storage.requireOwned(14, image.imageKey()));
        assertThrows(ResponseStatusException.class, () -> storage.requireOwned(5, "record-images/5/../../outside.png"));
    }

    @Test
    void rejectsNonImageAndCleansTemporaryFile() throws Exception {
        var storage = new RecordImageStorageService(directory.toString());
        assertThrows(ResponseStatusException.class, () -> storage.store(5,
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        try (var files = Files.list(directory.resolve("record-images/5"))) { assertEquals(0, files.count()); }
    }
}
