package com.back.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProfileRequest(
        @NotBlank
        @Size(max = 50)
        String nickname,

        String primaryPersona,

        List<String> personaTags
) {
}
