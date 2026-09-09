package com.back.course.map.storage.s3;

import com.back.course.map.storage.CourseMapImageStorage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3CourseMapImageStorage implements CourseMapImageStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3CourseMapImageStorage(
            @Qualifier("s3Client") S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.public-base-url}") String publicBaseUrl
    ) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3_BUCKET은 필수입니다.");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("S3_PUBLIC_BASE_URL은 필수입니다.");
        }
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String upload(Long courseId, byte[] image) {
        String objectKey = "course-maps/" + courseId + ".png";
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("image/png")
                .contentLength((long) image.length)
                .cacheControl("public, max-age=3600")
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(image));
        return publicBaseUrl + "/" + objectKey;
    }
}
