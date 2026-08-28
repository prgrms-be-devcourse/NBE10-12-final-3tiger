package com.back.user.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProfileImageStorage {

    String upload(Long userId, MultipartFile file);
}
