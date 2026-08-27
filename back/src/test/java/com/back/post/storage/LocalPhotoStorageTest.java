package com.back.post.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPhotoStorageTest {

    private final LocalPhotoStorage storage = new LocalPhotoStorage("http://localhost:8080/local-uploads");

    @Test
    void createsTargetOnlyForSupportedContentType() {
        assertThat(storage.createUploadTarget(1L, "walk.jpg", "image/jpeg").photoUrl()).endsWith(".jpg");
        assertThat(storage.createUploadTarget(1L, "walk.png", "image/png").photoUrl()).endsWith(".png");
        assertThat(storage.createUploadTarget(1L, "walk.webp", "image/webp").photoUrl()).endsWith(".webp");
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> storage.createUploadTarget(1L, "walk.gif", "image/gif"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다: image/gif");
    }
}
