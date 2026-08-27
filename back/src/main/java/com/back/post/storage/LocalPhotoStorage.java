package com.back.post.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class LocalPhotoStorage implements PhotoStorage {
    private final String publicBaseUrl;
    public LocalPhotoStorage(@Value("${app.storage.public-base-url}") String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    @Override public UploadTarget createUploadTarget(String fileName, String contentType) {
        String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String photoUrl = publicBaseUrl + "/posts/" + UUID.randomUUID() + "-" + safeName;
        return new UploadTarget(photoUrl, photoUrl, 300);
    }
}
