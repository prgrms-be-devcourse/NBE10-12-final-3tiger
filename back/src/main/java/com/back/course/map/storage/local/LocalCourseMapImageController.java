package com.back.course.map.storage.local;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/local-uploads/course-maps")
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalCourseMapImageController {

    private final Path uploadDirectory;

    public LocalCourseMapImageController(
            @Value("${app.storage.local-directory:build/local-uploads}") String directory
    ) {
        this.uploadDirectory = Path.of(directory).toAbsolutePath().normalize().resolve("course-maps");
    }

    @GetMapping("/{fileName:\\d+\\.png}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) throws MalformedURLException {
        Path file = uploadDirectory.resolve(fileName).normalize();
        if (!file.startsWith(uploadDirectory) || !file.toFile().isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(new UrlResource(file.toUri()));
    }
}
