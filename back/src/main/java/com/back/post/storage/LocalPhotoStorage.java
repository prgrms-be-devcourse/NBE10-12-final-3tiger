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
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String photoUrl = publicBaseUrl + "/posts/" + userId + "-" + UUID.randomUUID() + extension;
        return new UploadTarget(photoUrl, photoUrl, 300);
    }
}
