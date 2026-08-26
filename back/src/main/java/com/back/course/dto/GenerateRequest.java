package com.back.course.dto;

import com.back.course.domain.Persona;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record GenerateRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull @Min(500) @Max(10000) Integer distanceM,
        LocalDateTime at,
        Persona persona
) {
    public LocalDateTime atOrNow() {
        return at != null ? at : LocalDateTime.now();
    }
}
