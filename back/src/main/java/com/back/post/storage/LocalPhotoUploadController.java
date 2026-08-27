package com.back.post.storage;

import com.back.global.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/local-uploads/posts")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPhotoUploadController {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final Path uploadDirectory;

    public LocalPhotoUploadController(@Value("${app.storage.local-directory:build/local-uploads}") String directory) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("posts");
    }

    @PutMapping(value = "/{fileName}", consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ResponseEntity<Void> upload(@PathVariable String fileName,
                                       @RequestHeader("Content-Type") String contentType,
                                       @RequestBody byte[] content) {
        if (content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이미지는 1바이트 이상 10MB 이하여야 합니다.");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다.");
        }
        try {
            Files.createDirectories(uploadDirectory);
            Files.write(uploadDirectory.resolve(fileName), content);
            return ResponseEntity.noContent().build();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장할 수 없습니다.");
        }
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다.");
        }
        Path file = uploadDirectory.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 이미지입니다.");
        }
        MediaType mediaType = fileName.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.toLowerCase().endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }
}
