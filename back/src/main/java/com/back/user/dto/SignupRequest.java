package com.back.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank
        @Email
        @Pattern(regexp = "^[^@\\s]+@(?:[^@\\s.]+\\.)+[^@\\s.]+$")
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min =8)
        String password,

        @NotBlank
        @Size(max = 50)
        String nickname
) {
}
