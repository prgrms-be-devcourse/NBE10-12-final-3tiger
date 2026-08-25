package com.back.post.controller;

import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import com.back.post.service.PostLikeService;
import org.springframework.web.bind.annotation.*;

@RestController
public class PostLikeController {
    private final PostLikeService service;
    public PostLikeController(PostLikeService service) { this.service = service; }

    @PutMapping("/api/v1/posts/{postId}/likes")
    ApiResponse<PostLikeService.LikeResult> like(@CurrentUserId Long userId, @PathVariable Long postId) {
        return ApiResponse.ok("게시물 좋아요가 등록되었습니다.", service.like(postId, userId));
    }
    @DeleteMapping("/api/v1/posts/{postId}/likes")
    ApiResponse<PostLikeService.LikeResult> unlike(@CurrentUserId Long userId, @PathVariable Long postId) {
        return ApiResponse.ok("게시물 좋아요가 취소되었습니다.", service.unlike(postId, userId));
    }
    @GetMapping("/api/v1/users/me/likes")
    ApiResponse<PageResponse<PostLikeService.LikedPostItem>> myLikes(@CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("내 좋아요 목록 조회 성공", service.myLikes(userId, page, size));
    }
}
