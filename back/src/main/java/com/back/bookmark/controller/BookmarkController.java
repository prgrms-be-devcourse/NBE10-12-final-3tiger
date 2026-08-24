package com.back.bookmark.controller;

import com.back.bookmark.service.BookmarkService;
import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/users/me/bookmarks")
    ApiResponse<PageResponse<BookmarkService.BookmarkItem>> mine(@CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("저장 목록 조회 성공", service.mine(userId, page, size));
    }
}
