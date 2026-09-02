package com.back.hazard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record HazardCreateRequest(
        @NotBlank(message = "위험 유형은 필수입니다.")
        @Size(max = 50, message = "위험 유형은 50자 이하여야 합니다.")
        String hazardType,

        @NotBlank(message = "심각도는 필수입니다.")
        @Size(max = 20, message = "심각도는 20자 이하여야 합니다.")
        String severity,

        @NotBlank(message = "신고 내용은 필수입니다.")
        @Size(max = 1000, message = "신고 내용은 1000자 이하여야 합니다.")
        String content,

        @NotNull(message = "만료 시각은 필수입니다.")
        LocalDateTime expiresAt
) {
}
