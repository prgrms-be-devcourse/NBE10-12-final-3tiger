package com.back.user.email.dto;

import com.back.user.validation.EmailValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmRequest(
        @NotBlank
        @Email
        @Pattern(regexp = EmailValidation.REGEX)
        @Size(max = EmailValidation.MAX_LENGTH)
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code
) {
}
