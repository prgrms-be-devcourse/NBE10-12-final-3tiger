package com.back.grid.controller;

import com.back.global.api.ApiResponse;
import com.back.grid.dto.GridOverlayResponse;
import com.back.grid.service.GridService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grids")
@RequiredArgsConstructor
public class GridController {
    private final GridService gridService;

    @GetMapping
    ApiResponse<List<GridOverlayResponse>> findOverlays(
            @RequestParam(required = false) String bbox
    ) {
        return ApiResponse.ok(
                "격자 오버레이 조회 성공",
                gridService.findOverlays(bbox)
        );
    }
}
