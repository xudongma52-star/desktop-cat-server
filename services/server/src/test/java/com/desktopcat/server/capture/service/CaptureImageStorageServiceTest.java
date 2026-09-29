package com.desktopcat.server.capture.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class CaptureImageStorageServiceTest {
    @TempDir
    Path storageRoot;

    @Test
    void storesOriginalImageAndAcceptsIdenticalRetry() throws Exception {
        CaptureImageStorageService storage = new CaptureImageStorageService(storageRoot);
        byte[] png = png();
        MockMultipartFile image = new MockMultipartFile("image", "screen.png", "image/png", png);

        var first = storage.store(7, "f125ac71-5e65-4250-a992-425122b75509", image);
        var retry = storage.store(7, "f125ac71-5e65-4250-a992-425122b75509", image);

        assertTrue(first.createdNew());
        assertFalse(retry.createdNew());
        assertEquals("image/png", storage.contentType(first.storageKey()));
        assertArrayEquals(png, Files.readAllBytes(storage.requireExisting(first.storageKey())));
        assertTrue(storage.requireExisting(first.storageKey()).startsWith(storageRoot));
    }

    @Test
    void rejectsDifferentBytesForTheSameCaptureId() throws Exception {
        CaptureImageStorageService storage = new CaptureImageStorageService(storageRoot);
        String id = "f125ac71-5e65-4250-a992-425122b75509";
        storage.store(7, id, new MockMultipartFile("image", "one.png", "image/png", png()));
        BufferedImage other = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(other, "png", output);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> storage.store(7, id, new MockMultipartFile(
                        "image", "two.png", "image/png", output.toByteArray())));
        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void rejectsNonImageContent() {
        CaptureImageStorageService storage = new CaptureImageStorageService(storageRoot);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> storage.store(7, "f125ac71-5e65-4250-a992-425122b75509",
                        new MockMultipartFile("image", "fake.png", "image/png", new byte[] {1, 2, 3})));
        assertEquals(400, exception.getStatusCode().value());
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
