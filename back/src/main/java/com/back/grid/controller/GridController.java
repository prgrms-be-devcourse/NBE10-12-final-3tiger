package com.back.grid.controller;

import com.back.global.api.ApiResponse;
import com.back.grid.dto.GridOverlayResponse;
import com.back.grid.service.GridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grids")
@RequiredArgsConstructor
@Tag(name = "Grid", description = "지도 화면의 격자 스코어 오버레이 조회 API")
public class GridController {
    private final GridService gridService;

    @GetMapping
    @Operation(
            summary = "bbox 내 격자 스코어 오버레이 조회",
            description = "지도 영역을 나타내는 bbox(minLng,minLat,maxLng,maxLat) 안에 중심점이 위치한 격자의 좌표와 환경 스코어를 조회합니다."
    )
    ApiResponse<List<GridOverlayResponse>> findOverlays(
            @RequestParam(required = false) String bbox
    ) {
        return ApiResponse.ok(
                "격자 오버레이 조회 성공",
                gridService.findOverlays(bbox)
        );
    }
}
