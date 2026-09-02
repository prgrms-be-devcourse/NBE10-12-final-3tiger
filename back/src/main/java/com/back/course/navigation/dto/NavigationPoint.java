package com.back.course.navigation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "위치 좌표")
public record NavigationPoint(

        @Schema(
                description = "위도",
                example = "37.544"
        )
        double lat,

        @Schema(
                description = "경도",
                example = "127.037"
        )
        double lng
) { }
