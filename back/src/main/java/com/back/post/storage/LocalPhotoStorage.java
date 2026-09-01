package com.back.post.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {
    private final String publicBaseUrl;
    private final Path uploadDirectory;

    @Autowired
    public LocalPhotoStorage(
            @Value("${app.storage.public-base-url}") String publicBaseUrl,
            @Value("${app.storage.local-directory:build/local-uploads}") String directory
    ) {
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("posts");
    }

    LocalPhotoStorage(String publicBaseUrl) {
        this(publicBaseUrl, "build/local-uploads");
    }
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

    @Override
    public void delete(Long userId, String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return;

        try {
            String path = URI.create(photoUrl).getPath();
            String prefix = "/local-uploads/posts/";
            if (path == null || !path.startsWith(prefix)) return;

            String fileName = path.substring(prefix.length());
            if (!fileName.startsWith(userId + "-")
                    || fileName.contains("/")
                    || fileName.contains("\\")
                    || fileName.contains("..")) return;

            Files.deleteIfExists(uploadDirectory.resolve(fileName));
        } catch (IllegalArgumentException | IOException ignored) {
            // 파일 정리 실패가 게시글 삭제/등록 실패 처리를 막지는 않도록 한다.
        }
    }
}
