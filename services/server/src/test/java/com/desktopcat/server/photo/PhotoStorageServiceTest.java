package com.desktopcat.server.photo.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PhotoStorageServiceTest {
    @TempDir
    Path storageRoot;

    @Test
    void storesAValidatedFixedSizeWebpBelowTheConfiguredRoot() throws Exception {
        byte[] webp = lossyWebp(1200, 900);
        PhotoStorageService storage = new PhotoStorageService(
                storageRoot, Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC));

        String key = storage.store(7L, new MockMultipartFile(
                "file", "memory.webp", "image/webp", webp));

        assertTrue(key.startsWith("photos/7/2026/09/"));
        Path stored = storage.requireExisting(key);
        assertTrue(stored.startsWith(storageRoot));
        assertArrayEquals(webp, Files.readAllBytes(stored));

        storage.deleteQuietly(key);
        assertFalse(Files.exists(stored));
    }

    @Test
    void rejectsWebpWhenItsDimensionsDoNotMatchTheCarouselContract() {
        PhotoStorageService storage = new PhotoStorageService(
                storageRoot, Clock.systemUTC());
        MockMultipartFile file = new MockMultipartFile(
                "file", "wide.webp", "image/webp", lossyWebp(1600, 900));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> storage.store(7L, file));

        assertEquals("Photo dimensions must be 1200 by 900.", exception.getReason());
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsContentThatOnlyClaimsToBeWebp() {
        PhotoStorageService storage = new PhotoStorageService(
                storageRoot, Clock.systemUTC());
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.webp", "image/webp", "not an image".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> storage.store(7L, file));

        assertEquals("Photo file is invalid.", exception.getReason());
    }

    private byte[] lossyWebp(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'R', 'I', 'F', 'F'});
        buffer.putInt(22);
        buffer.put(new byte[] {'W', 'E', 'B', 'P'});
        buffer.put(new byte[] {'V', 'P', '8', ' '});
        buffer.putInt(10);
        buffer.put(new byte[3]);
        buffer.put(new byte[] {(byte) 0x9d, 0x01, 0x2a});
        buffer.putShort((short) width);
        buffer.putShort((short) height);
        return buffer.array();
    }
}
