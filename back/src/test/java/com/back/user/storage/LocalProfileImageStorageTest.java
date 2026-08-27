package com.back.user.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalProfileImageStorageTest {

    @TempDir
    Path tempDirectory;

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, jpg",
            "image/png, png",
            "image/webp, webp"
    })
    void uploadsImageWithExtensionDeterminedByContentType(String contentType, String extension) throws Exception {
        LocalProfileImageStorage storage = new LocalProfileImageStorage(
                tempDirectory.toString(),
                "http://localhost:8080/local-uploads/"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ignored-name.bin",
                contentType,
                new byte[]{1, 2, 3}
        );

        String url = storage.upload(1L, file);

        assertThat(url)
                .startsWith("http://localhost:8080/local-uploads/profile-images/")
                .endsWith("." + extension);
        Path storedFile = tempDirectory.resolve("profile-images")
                .resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(storedFile).exists();
        assertThat(Files.readAllBytes(storedFile)).containsExactly(new byte[]{1, 2, 3});
    }

    @Test
    void createsProfileImageDirectoryAutomatically() {
        LocalProfileImageStorage storage = new LocalProfileImageStorage(
                tempDirectory.resolve("nested").toString(),
                "http://localhost:8080/local-uploads"
        );

        storage.upload(1L, new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", new byte[]{1}
        ));

        assertThat(tempDirectory.resolve("nested/profile-images")).isDirectory();
    }
}
