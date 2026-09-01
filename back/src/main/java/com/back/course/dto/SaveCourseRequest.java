package com.back.course.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 선택한 코스 저장 요청.
 * - isLoop 기본값 true (기존 클라이언트 하위호환)
 * - 편도로 저장 시: isLoop=false + endLat/endLng 지정 권장 (없으면 path 마지막 점 사용)
 */
public record SaveCourseRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull GeoJsonLineString path,
        @NotNull String regionCode,
        Boolean isLoop,
        Double endLat,
        Double endLng
) {
    public boolean isLoopOrDefault() {
        return isLoop == null || isLoop;
    }
}
