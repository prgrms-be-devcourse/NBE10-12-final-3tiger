package com.back.hazard.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardCreateResponse;
import com.back.hazard.dto.HazardResponse;
import com.back.hazard.dto.HazardUpvoteResponse;
import com.back.hazard.service.HazardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Hazard", description = "코스 위험 신고 API")
public class HazardController {

    private final HazardService hazardService;

    public HazardController(HazardService hazardService) {
        this.hazardService = hazardService;
    }

    @GetMapping("/courses/{courseId}/hazards")
    @Operation(
            summary = "코스 활성 위험 신고 조회",
            description = "코스의 위험 신고 중 현재 시각을 기준으로 만료되지 않은 신고를 조회합니다."
    )
    ApiResponse<List<HazardResponse>> getActiveHazards(@PathVariable Long courseId) {
        return ApiResponse.ok("코스 위험 신고 조회 성공", hazardService.getActiveHazards(courseId));
    }

    @PostMapping("/courses/{courseId}/hazards")
    @Operation(summary = "코스 위험 신고 등록")
    ApiResponse<HazardCreateResponse> create(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody HazardCreateRequest request
    ) {
        return ApiResponse.ok("위험 신고가 등록되었습니다.", hazardService.create(userId, courseId, request));
    }

    @DeleteMapping("/hazards/{hazardId}")
    @Operation(summary = "위험 신고 삭제")
    ApiResponse<Void> delete(
            @CurrentUserId Long userId,
            @PathVariable Long hazardId
    ) {
        hazardService.delete(userId, hazardId);
        return ApiResponse.ok("위험 신고가 삭제되었습니다.", null);
    }

    @PostMapping("/hazards/{hazardId}/upvote")
    @Operation(summary = "위험 신고 공감")
    ApiResponse<HazardUpvoteResponse> upvote(
            @CurrentUserId Long userId,
            @PathVariable Long hazardId
    ) {
        return ApiResponse.ok("공감이 반영되었습니다.", hazardService.upvote(hazardId));
    }
}
