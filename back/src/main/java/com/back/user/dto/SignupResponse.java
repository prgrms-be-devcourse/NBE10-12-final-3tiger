package com.back.user.dto;

import com.back.user.domain.Provider;
import com.back.user.domain.User;

public record SignupResponse(
        Long userId,
        String loginType
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getProvider() == Provider.LOCAL ? "NORMAL" : user.getProvider().name()
        );
    }
}
