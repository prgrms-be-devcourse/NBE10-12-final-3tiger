package com.back.post.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {
    private final String publicBaseUrl;
    public LocalPhotoStorage(@Value("${app.storage.public-base-url}") String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    @Override public UploadTarget createUploadTarget(Long userId, String fileName, String contentType) {
        if (contentType == null) throw new IllegalArgumentException("이미지 형식은 필수입니다.");
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다: " + contentType);
        };
        String photoUrl = publicBaseUrl + "/posts/" + userId + "-" + UUID.randomUUID() + extension;
        return new UploadTarget(photoUrl, photoUrl, 300);
    }
}
