package com.back.user.storage;

import com.back.global.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalProfileImageControllerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void servesStoredProfileImageWithMatchingContentType() throws Exception {
        Path directory = Files.createDirectories(tempDirectory.resolve("profile-images"));
        Files.write(directory.resolve("profile.jpg"), new byte[]{1, 2, 3});
        LocalProfileImageController controller = new LocalProfileImageController(tempDirectory.toString());

        var response = controller.download("profile.jpg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("image/jpeg");
    }

    @Test
    void rejectsPathTraversal() {
        LocalProfileImageController controller = new LocalProfileImageController(tempDirectory.toString());

        assertThatThrownBy(() -> controller.download("../secret.jpg"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
