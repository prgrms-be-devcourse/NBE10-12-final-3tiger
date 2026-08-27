package com.back.post.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3PhotoStorage implements PhotoStorage {
    private static final int EXPIRES_IN_SECONDS = 300;

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3PhotoStorage(S3Presigner presigner,
                          @Value("${app.storage.s3.bucket}") String bucket,
                          @Value("${app.storage.s3.public-base-url}") String publicBaseUrl) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3_BUCKET은 필수입니다.");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("S3_PUBLIC_BASE_URL은 필수입니다.");
        }
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public UploadTarget createUploadTarget(Long userId, String fileName, String contentType) {
        String objectKey = objectKey(userId, contentType);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(EXPIRES_IN_SECONDS))
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = presigner.presignPutObject(presignRequest).url().toString();
        return new UploadTarget(uploadUrl, publicBaseUrl + "/" + objectKey, EXPIRES_IN_SECONDS);
    }

    private String objectKey(Long userId, String contentType) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "posts/%d/%04d/%02d/%s%s".formatted(
                userId, today.getYear(), today.getMonthValue(), UUID.randomUUID(), extensionFor(contentType));
    }

    private String extensionFor(String contentType) {
        if (contentType == null) throw new IllegalArgumentException("이미지 형식은 필수입니다.");
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다: " + contentType);
        };
    }
}
