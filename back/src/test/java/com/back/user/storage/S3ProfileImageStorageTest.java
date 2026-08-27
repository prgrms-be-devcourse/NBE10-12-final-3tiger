package com.back.user.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ProfileImageStorageTest {

    @Test
    void uploadsImageToUserProfilePathAndReturnsPublicUrl() {
        S3Client s3Client = mock(S3Client.class);
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        S3ProfileImageStorage storage = new S3ProfileImageStorage(
                s3Client,
                "walking-images",
                "https://cdn.example.com/"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file", "ignored.png", "image/png", new byte[]{1, 2, 3}
        );

        String url = storage.upload(17L, file);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("walking-images");
        assertThat(request.key()).startsWith("profile-images/17/").endsWith(".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(3L);
        assertThat(bodyCaptor.getValue().optionalContentLength()).contains(3L);
        assertThat(url).isEqualTo("https://cdn.example.com/" + request.key());
    }
}
