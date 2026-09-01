package com.back.post.storage;

public interface PhotoStorage {
    UploadTarget createUploadTarget(Long userId, String fileName, String contentType);

    /** 사용자 소유의 게시글 사진만 삭제한다. 이미 삭제된 파일은 성공으로 처리한다. */
    void delete(Long userId, String photoUrl);

    record UploadTarget(String uploadUrl, String photoUrl, int expireInSeconds) {}
}
