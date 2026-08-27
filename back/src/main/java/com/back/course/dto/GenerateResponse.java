package com.back.course.dto;

import java.util.List;

public record GenerateResponse(
        List<GenerateCandidate> candidates,
        int requestedCount,
        int returnedCount
) {}
