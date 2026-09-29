package com.desktopcat.server.capture.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Stores original capture images separately from fixed-size carousel photos. */
@Service
public class CaptureImageStorageService {
    private final Path storageRoot;

    @Autowired
    public CaptureImageStorageService(
            @Value("${desktop-cat.photo.storage-root:./data}") String storageRoot) {
        this(Path.of(storageRoot));
    }

    CaptureImageStorageService(Path storageRoot) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    public StoredImage store(long userId, String captureId, MultipartFile file) {
        byte[] bytes = read(file);
        String extension = extension(bytes);
        String storageKey = "captures/%d/%s.%s".formatted(userId, captureId, extension);
        Path target = resolve(storageKey);
        Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) return existing(storageKey, target, bytes, extension);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            } catch (FileAlreadyExistsException exception) {
                return existing(storageKey, target, bytes, extension);
            }
            return new StoredImage(storageKey, mimeTypeForExtension(extension), bytes.length, true);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Capture image could not be stored.", exception);
        } finally {
            deletePathQuietly(temporary);
        }
    }

    public Path requireExisting(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Capture image file is unavailable.");
        }
        return path;
    }

    public String contentType(String storageKey) {
        int dot = storageKey.lastIndexOf('.');
        return mimeTypeForExtension(dot < 0 ? "" : storageKey.substring(dot + 1));
    }

    public void deleteQuietly(String storageKey) {
        deletePathQuietly(resolve(storageKey));
    }

    private StoredImage existing(String storageKey, Path target, byte[] bytes, String extension)
            throws IOException {
        if (!Arrays.equals(Files.readAllBytes(target), bytes)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Capture id has already been used.");
        }
        return new StoredImage(storageKey, mimeTypeForExtension(extension), bytes.length, false);
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capture image is required.");
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Capture image is invalid.", exception);
        }
    }

    private String extension(byte[] bytes) {
        if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) {
            if (decodesWithImageIo(bytes)) return "png";
        } else if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            if (decodesWithImageIo(bytes)) return "jpg";
        } else if (validWebp(bytes)) {
            return "webp";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capture image is invalid.");
    }

    private boolean decodesWithImageIo(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean validWebp(byte[] bytes) {
        if (bytes.length < 30 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")
                || Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt()) + 8 != bytes.length) return false;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String type = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt());
            int data = offset + 8;
            if (size > bytes.length - data) return false;
            if ("VP8 ".equals(type) && size >= 10
                    && bytes[data + 3] == (byte) 0x9d
                    && bytes[data + 4] == 0x01 && bytes[data + 5] == 0x2a) return true;
            if ("VP8L".equals(type) && size >= 5 && bytes[data] == 0x2f) return true;
            offset = data + (int) size + ((size & 1) == 1 ? 1 : 0);
        }
        return false;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        return new String(bytes, offset, expected.length(),
                java.nio.charset.StandardCharsets.US_ASCII).equals(expected);
    }

    private String mimeTypeForExtension(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Capture image file is unavailable.");
        };
    }

    private Path resolve(String storageKey) {
        Path path = storageRoot.resolve(storageKey).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Capture image file is unavailable.");
        }
        return path;
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // File cleanup must not hide the original database or upload failure.
        }
    }

    public record StoredImage(String storageKey, String contentType, long size, boolean createdNew) {
    }
}
