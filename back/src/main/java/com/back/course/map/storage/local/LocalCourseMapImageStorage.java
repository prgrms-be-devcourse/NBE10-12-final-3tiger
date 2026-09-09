package com.back.course.map.storage.local;

import com.back.course.map.storage.CourseMapImageStorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalCourseMapImageStorage implements CourseMapImageStorage {

    private final Path uploadDirectory;
    private final String publicBaseUrl;

    public LocalCourseMapImageStorage(
            @Value("${app.storage.local-directory:build/local-uploads}") String directory,
            @Value("${app.storage.public-base-url:http://localhost:8080/local-uploads}") String publicBaseUrl
    ) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("course-maps");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String upload(Long courseId, byte[] image) {
        String fileName = courseId + ".png";
        Path temporaryFile = uploadDirectory.resolve(fileName + ".tmp").normalize();
        Path targetFile = uploadDirectory.resolve(fileName).normalize();
        if (!targetFile.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException("잘못된 코스 지도 이미지 경로입니다.");
        }

        try {
            Files.createDirectories(uploadDirectory);
            Files.write(temporaryFile, image);
            try {
                Files.move(temporaryFile, targetFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("코스 지도 이미지를 저장할 수 없습니다.", exception);
        }
        return publicBaseUrl + "/course-maps/" + fileName;
    }
}
