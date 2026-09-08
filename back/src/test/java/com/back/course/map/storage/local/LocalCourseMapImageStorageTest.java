package com.back.course.map.storage.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCourseMapImageStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesGeneratedImageInCourseMapDirectory() throws Exception {
        var storage = new LocalCourseMapImageStorage(
                temporaryDirectory.toString(),
                "http://localhost:8080/local-uploads/"
        );
        byte[] image = {1, 2, 3, 4};

        String imageUrl = storage.upload(44L, image);

        assertThat(imageUrl).isEqualTo("http://localhost:8080/local-uploads/course-maps/44.png");
        assertThat(Files.readAllBytes(temporaryDirectory.resolve("course-maps/44.png")))
                .containsExactly(image);
    }
}
