package com.back.user.storage;

import com.back.global.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@RestController
@RequestMapping("/local-uploads/profile-images")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalProfileImageController {

    private final Path uploadDirectory;

    public LocalProfileImageController(
            @Value("${app.storage.local-directory:build/local-uploads}") String directory
    ) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("profile-images");
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path file = resolveFile(fileName);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 이미지입니다.");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(contentType(fileName))
                .body(new FileSystemResource(file));
    }

    private Path resolveFile(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다.");
        }

        Path file = uploadDirectory.resolve(fileName).normalize();
        if (!file.startsWith(uploadDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다.");
        }
        return file;
    }

    private MediaType contentType(String fileName) {
        String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseFileName.endsWith(".jpg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerCaseFileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerCaseFileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다.");
    }
}
