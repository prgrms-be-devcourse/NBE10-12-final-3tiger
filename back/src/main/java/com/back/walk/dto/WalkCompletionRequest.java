package com.back.walk.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record WalkCompletionRequest(
        @NotNull OffsetDateTime startedAt,
        @NotNull OffsetDateTime finishedAt
) {}
