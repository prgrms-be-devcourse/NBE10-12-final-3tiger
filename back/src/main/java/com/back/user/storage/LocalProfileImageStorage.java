package com.back.user.storage;

import com.back.global.error.ApiException;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalProfileImageStorage implements ProfileImageStorage {

    private final Path uploadDirectory;
    private final String publicBaseUrl;

    public LocalProfileImageStorage(
            @Value("${app.storage.local-directory:build/local-uploads}") String directory,
            @Value("${app.storage.public-base-url:http://localhost:8080/local-uploads}") String publicBaseUrl
    ) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("profile-images");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String fileName = UUID.randomUUID() + "." + extensionFor(file.getContentType());
        Path target = uploadDirectory.resolve(fileName).normalize();

        if (!target.startsWith(uploadDirectory)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 파일명입니다.");
        }

        try {
            Files.createDirectories(uploadDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지를 저장할 수 없습니다.");
        }

        return publicBaseUrl + "/profile-images/" + fileName;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        };
    }
}
