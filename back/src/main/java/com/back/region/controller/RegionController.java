package com.back.region.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@Tag(name = "Region", description = "산책 코스를 제공하는 서비스 지역 조회 API")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "서비스 지역 목록 조회",
            description = "서비스 중인 지역의 행정구역 코드, 이름, 중심 좌표와 지역별 코스 개수를 조회합니다. 인증 없이 사용할 수 있습니다."
    )
    ApiResponse<List<RegionService.RegionItem>> list() {
        return ApiResponse.ok("지역 목록 조회 성공", service.list());
    }
}
