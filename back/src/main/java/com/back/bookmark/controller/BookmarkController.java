package com.back.bookmark.controller;

import com.back.bookmark.service.BookmarkService;
import com.back.bookmark.dto.RatingRequest;
import com.back.bookmark.dto.UsageLogRequest;
import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1")
public class BookmarkController {
    private final BookmarkService service;
    public BookmarkController(BookmarkService service) { this.service = service; }

    @PutMapping("/courses/{courseId}/bookmarks")
    ApiResponse<BookmarkService.BookmarkState> add(@CurrentUserId Long userId, @PathVariable Long courseId) {
        return ApiResponse.ok("코스가 저장되었습니다.", service.add(userId, courseId));
    }
    @DeleteMapping("/courses/{courseId}/bookmarks")
    ApiResponse<BookmarkService.BookmarkState> remove(@CurrentUserId Long userId, @PathVariable Long courseId) {
        return ApiResponse.ok("코스 저장이 취소되었습니다.", service.remove(userId, courseId));
    }
    @PatchMapping("/courses/{courseId}/bookmarks/rating")
    ApiResponse<BookmarkService.BookmarkItem> rate(@CurrentUserId Long userId, @PathVariable Long courseId,
                                                    @Valid @RequestBody RatingRequest request) {
        return ApiResponse.ok("코스 별점이 저장되었습니다.", service.rate(userId, courseId, request.rating()));
    }
    @PostMapping("/courses/{courseId}/bookmarks/usage-logs")
    ApiResponse<BookmarkService.UsageLogItem> recordUsage(@CurrentUserId Long userId, @PathVariable Long courseId,
                                                           @RequestBody(required = false) UsageLogRequest request) {
        LocalDateTime usedAt = request == null || request.usedAt() == null
                ? LocalDateTime.now()
                : request.usedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        return ApiResponse.ok("코스 사용 기록이 저장되었습니다.", service.recordUsage(userId, courseId, usedAt));
    }
    @GetMapping("/courses/{courseId}/bookmarks/usage-logs")
    ApiResponse<PageResponse<BookmarkService.UsageLogItem>> usageLogs(@CurrentUserId Long userId,
            @PathVariable Long courseId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("코스 사용 기록 조회 성공", service.usageLogs(userId, courseId, page, size));
    }
    @GetMapping("/users/me/bookmarks")
    ApiResponse<PageResponse<BookmarkService.BookmarkItem>> mine(@CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("저장 목록 조회 성공", service.mine(userId, page, size));
    }
}
