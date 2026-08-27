package com.back.post.storage;

public interface PhotoStorage {
    UploadTarget createUploadTarget(Long userId, String fileName, String contentType);
    record UploadTarget(String uploadUrl, String photoUrl, int expireInSeconds) {}
}
