package com.desktopcat.server.photo.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoStorageService {
    static final int REQUIRED_WIDTH = 1200;
    static final int REQUIRED_HEIGHT = 900;
    static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final Path storageRoot;
    private final Clock clock;

    @Autowired
    public PhotoStorageService(
            @Value("${desktop-cat.photo.storage-root:./data}") String storageRoot) {
        this(Path.of(storageRoot), Clock.systemUTC());
    }

    PhotoStorageService(Path storageRoot, Clock clock) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public String store(long userId, MultipartFile file) {
        byte[] bytes = validateAndRead(file);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String storageKey = "photos/%d/%d/%02d/%s.webp".formatted(
                userId, today.getYear(), today.getMonthValue(), UUID.randomUUID());
        Path target = resolve(storageKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            return storageKey;
        } catch (IOException exception) {
            deletePathQuietly(temporary);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be stored.", exception);
        }
    }

    public Path requireExisting(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo file is unavailable.");
        }
        return path;
    }

    public void deleteQuietly(String storageKey) {
        deletePathQuietly(resolve(storageKey));
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Photo file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Photo file must not exceed 2 MB.");
        }
        if (!"image/webp".equalsIgnoreCase(file.getContentType())) {
            throw badRequest("Photo file must be WebP.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_FILE_SIZE) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "Photo file must not exceed 2 MB.");
            }
            ImageSize size = readWebpSize(bytes);
            if (size.width() != REQUIRED_WIDTH || size.height() != REQUIRED_HEIGHT) {
                throw badRequest("Photo dimensions must be 1200 by 900.");
            }
            return bytes;
        } catch (IOException exception) {
            throw badRequest("Photo file is invalid.");
        }
    }

    private ImageSize readWebpSize(byte[] bytes) {
        if (bytes.length < 30
                || !asciiEquals(bytes, 0, "RIFF")
                || !asciiEquals(bytes, 8, "WEBP")
                || Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8 != bytes.length) {
            throw badRequest("Photo file is invalid.");
        }

        int offset = 12;
        ImageSize extendedSize = null;
        while (offset + 8 <= bytes.length) {
            String chunkType = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt());
            int dataOffset = offset + 8;
            if (chunkSize > Integer.MAX_VALUE || dataOffset + chunkSize > bytes.length) {
                throw badRequest("Photo file is invalid.");
            }

            ImageSize size = switch (chunkType) {
                case "VP8X" -> {
                    extendedSize = readExtendedSize(bytes, dataOffset, (int) chunkSize);
                    yield null;
                }
                case "VP8 " -> readLossySize(bytes, dataOffset, (int) chunkSize);
                case "VP8L" -> readLosslessSize(bytes, dataOffset, (int) chunkSize);
                default -> null;
            };
            if (size != null) {
                if (extendedSize != null && !extendedSize.equals(size)) {
                    throw badRequest("Photo file is invalid.");
                }
                return size;
            }
            offset = dataOffset + (int) chunkSize + ((chunkSize & 1) == 1 ? 1 : 0);
        }
        throw badRequest("Photo file is invalid.");
    }

    private ImageSize readExtendedSize(byte[] bytes, int offset, int size) {
        if (size < 10) throw badRequest("Photo file is invalid.");
        return new ImageSize(read24(bytes, offset + 4) + 1, read24(bytes, offset + 7) + 1);
    }

    private ImageSize readLossySize(byte[] bytes, int offset, int size) {
        if (size < 10
                || (bytes[offset + 3] & 0xff) != 0x9d
                || (bytes[offset + 4] & 0xff) != 0x01
                || (bytes[offset + 5] & 0xff) != 0x2a) {
            throw badRequest("Photo file is invalid.");
        }
        int width = read16(bytes, offset + 6) & 0x3fff;
        int height = read16(bytes, offset + 8) & 0x3fff;
        return new ImageSize(width, height);
    }

    private ImageSize readLosslessSize(byte[] bytes, int offset, int size) {
        if (size < 5 || (bytes[offset] & 0xff) != 0x2f) {
            throw badRequest("Photo file is invalid.");
        }
        int b1 = bytes[offset + 1] & 0xff;
        int b2 = bytes[offset + 2] & 0xff;
        int b3 = bytes[offset + 3] & 0xff;
        int b4 = bytes[offset + 4] & 0xff;
        int width = 1 + b1 + ((b2 & 0x3f) << 8);
        int height = 1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
        return new ImageSize(width, height);
    }

    private int read16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int read24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private boolean asciiEquals(byte[] bytes, int offset, String value) {
        return new String(bytes, offset, value.length(), StandardCharsets.US_ASCII).equals(value);
    }

    private Path resolve(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Photo file is unavailable.");
        }
        return resolved;
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不能覆盖原始业务异常，保留文件供运维排查。
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ImageSize(int width, int height) {
    }
}
