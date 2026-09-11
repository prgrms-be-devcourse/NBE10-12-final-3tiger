package com.back.walk.controller;

import com.back.global.api.ApiResponse;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserId;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.walk.dto.WalkCompletionRequest;
import com.back.walk.dto.WalkRecordResponse;
import com.back.walk.service.WalkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WalkController {

    private static final int MAX_PAGE_SIZE = 100;
    private final WalkService service;

    public WalkController(WalkService service) {
        this.service = service;
    }

    @PostMapping("/courses/{courseId}/walks")
    public ApiResponse<WalkRecordResponse> complete(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody WalkCompletionRequest request
    ) {
        return ApiResponse.ok("산책 기록이 저장되었습니다.", service.complete(userId, courseId, request));
    }

    @GetMapping("/users/me/walks")
    public ApiResponse<PageResponse<WalkRecordResponse>> getMyWalks(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.ok("내 산책 기록 조회 성공", service.getMyWalks(userId, page, size));
    }
}
