package com.back.course.dto;

import java.math.BigDecimal;

/**
 * 생성만 된 후보 (아직 DB 저장 X). 사용자가 선택 시 SaveCourseRequest 로 저장 요청.
 * errorPct는 순환(loop) 코스에만 의미가 있고, 편도(oneway)는 null.
 */
public record GenerateCandidate(
        GeoJsonLineString path,
        Integer totalM,
        BigDecimal avgScore,
        BigDecimal errorPct,
        String regionCode
) {}
