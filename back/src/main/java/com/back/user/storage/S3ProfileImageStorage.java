package com.back.user.storage;

import com.back.global.error.ApiException;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ProfileImageStorage implements ProfileImageStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ProfileImageStorage(
            @Qualifier("profileImageS3Client") S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.public-base-url}") String publicBaseUrl
    ) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3_BUCKET은 필수입니다.");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("S3_PUBLIC_BASE_URL은 필수입니다.");
        }
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String key = "profile-images/%d/%s.%s".formatted(
                userId,
                UUID.randomUUID(),
                extensionFor(file.getContentType())
        );
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지를 저장할 수 없습니다.");
        }

        return publicBaseUrl + "/" + key;
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
