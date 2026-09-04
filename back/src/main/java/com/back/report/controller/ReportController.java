package com.back.report.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.report.domain.ReportReason;
import com.back.report.domain.ReportTargetType;
import com.back.report.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/posts/{postId}/reports")
    ApiResponse<ReportService.ReportResult> reportPost(@CurrentUserId Long userId, @PathVariable Long postId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok("신고가 접수되었습니다.",
                service.report(userId, ReportTargetType.POST, postId, request.reason()));
    }

    @PostMapping("/comments/{commentId}/reports")
    ApiResponse<ReportService.ReportResult> reportComment(@CurrentUserId Long userId, @PathVariable Long commentId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok("신고가 접수되었습니다.",
                service.report(userId, ReportTargetType.COMMENT, commentId, request.reason()));
    }

    @PostMapping("/users/{userId}/reports")
    ApiResponse<ReportService.ReportResult> reportUser(@CurrentUserId Long userId, @PathVariable("userId") Long targetUserId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.ok("신고가 접수되었습니다.",
                service.report(userId, ReportTargetType.USER, targetUserId, request.reason()));
    }

    record ReportRequest(@NotNull(message = "신고 사유는 필수입니다.") ReportReason reason) {}
}
