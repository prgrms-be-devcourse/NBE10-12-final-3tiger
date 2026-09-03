package com.back.course.navigation.dto;

import com.back.course.dto.GeoJsonLineString;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "산책 코스 안내 정보")
public record CourseNavigationResponse(

        @Schema(example = "101")
        Long courseId,

        @Schema(example = "서울숲 순환 산책로")
        String name,

        @Schema(
                description = "전체 코스 거리(m)",
                example = "2500"
        )
        int distanceM,

        @Schema(
                description = "예상 소요 시간(분)",
                example = "35"
        )
        int estimatedMinutes,

        @Schema(
                description = "순환 코스 여부",
                example = "true"
        )
        boolean isLoop,

        @Schema(description = "코스 출발점")
        NavigationPoint startPoint,

        @Schema(description = "코스 도착점")
        NavigationPoint endPoint,

        @Schema(
                description = "GeoJSON LineString, 좌표는 [경도, 위도] 순서"
        )
        GeoJsonLineString path
) { }
