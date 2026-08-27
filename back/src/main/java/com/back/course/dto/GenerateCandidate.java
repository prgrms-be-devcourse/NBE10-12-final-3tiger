package com.back.course.dto;

import java.math.BigDecimal;

/**
 * 생성만 된 후보 (아직 DB 저장 X). 사용자가 선택 시 SaveCourseRequest 로 저장 요청.
 */
public record GenerateCandidate(
        GeoJsonLineString path,
        Integer totalM,
        BigDecimal avgScore,
        BigDecimal errorPct,
        String regionCode
) {}
