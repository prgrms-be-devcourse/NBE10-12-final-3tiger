package com.back.userblock.controller;

import com.back.global.api.ApiResponse;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserId;
import com.back.userblock.service.UserBlockService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserBlockController {

    private final UserBlockService service;

    public UserBlockController(UserBlockService service) {
        this.service = service;
    }

    @PutMapping("/{userId}/block")
    ApiResponse<UserBlockService.BlockResult> block(@CurrentUserId Long currentUserId,
            @PathVariable("userId") Long targetUserId) {
        return ApiResponse.ok("사용자를 차단했습니다.", service.block(currentUserId, targetUserId));
    }

    @DeleteMapping("/{userId}/block")
    ApiResponse<UserBlockService.BlockResult> unblock(@CurrentUserId Long currentUserId,
            @PathVariable("userId") Long targetUserId) {
        return ApiResponse.ok("사용자 차단을 해제했습니다.", service.unblock(currentUserId, targetUserId));
    }

    @GetMapping("/blocks")
    ApiResponse<PageResponse<UserBlockService.BlockedUser>> blocks(@CurrentUserId Long currentUserId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("차단 목록 조회 성공", service.myBlocks(currentUserId, page, size));
    }
}
