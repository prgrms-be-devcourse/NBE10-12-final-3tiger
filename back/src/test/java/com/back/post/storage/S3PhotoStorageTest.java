package com.back.post.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

class S3PhotoStorageTest {

    @Test
    void createsPresignedPutUrlAndPublicPhotoUrl() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            S3PhotoStorage storage = new S3PhotoStorage(
                    presigner, "today-walk-images", "https://images.example.com/");

            PhotoStorage.UploadTarget target = storage.createUploadTarget(
                    17L, "산책 사진.jpg", "image/jpeg");

            assertThat(target.uploadUrl())
                    .startsWith("https://today-walk-images.s3.ap-northeast-2.amazonaws.com/posts/17/")
                    .contains("X-Amz-Signature=");
            assertThat(target.photoUrl())
                    .startsWith("https://images.example.com/posts/17/")
                    .endsWith(".jpg");
            assertThat(target.expireInSeconds()).isEqualTo(300);
        }
    }
}
