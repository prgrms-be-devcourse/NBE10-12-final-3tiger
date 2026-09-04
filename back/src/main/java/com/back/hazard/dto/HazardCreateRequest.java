package com.back.hazard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude
) {
}
