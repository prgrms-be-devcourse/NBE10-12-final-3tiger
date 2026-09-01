package com.back.course.dto;

import com.back.course.domain.Persona;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 코스 후보 생성 요청.
 * - 순환(is_loop=true): endLat/endLng 없이 distanceM 필수 → 시작점 주변 순환 코스
 * - 편도(is_loop=false): endLat/endLng 필수, distanceM은 무시(도착까지의 실제 거리로 결정)
 */
public record GenerateRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        @Min(500) @Max(10000) Integer distanceM,
        Double endLat,
        Double endLng,
        LocalDateTime at,
        Persona persona
) {
    public LocalDateTime atOrNow() {
        return at != null ? at : LocalDateTime.now();
    }

    public boolean isOneway() {
        return endLat != null && endLng != null;
    }
}
