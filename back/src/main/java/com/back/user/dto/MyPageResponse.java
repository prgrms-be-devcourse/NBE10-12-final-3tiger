package com.back.user.dto;

import java.util.List;

public record MyPageResponse(
        Long userId,
        String nickname,
        String email,
        String loginType,
        String profileImageUrl,
        String primaryPersona,
        List<String> personaTags
) {
}
