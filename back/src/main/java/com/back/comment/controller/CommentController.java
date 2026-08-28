package com.back.comment.controller;

import com.back.comment.service.CommentService;
import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommentController {
    private final CommentService service;
    public CommentController(CommentService service) { this.service = service; }

    @GetMapping("/posts/{postId}/comments")
    ApiResponse<PageResponse<CommentService.CommentResponse>> comments(@CurrentUserId(required = false) Long userId,
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("댓글 목록 조회 성공", service.getComments(postId, userId, PageRequest.of(page, size)));
    }
    @PostMapping("/posts/{postId}/comments")
    ApiResponse<Long> create(@CurrentUserId Long userId, @PathVariable Long postId, @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok("댓글이 등록되었습니다.", service.createComment(postId, userId, request.content()));
    }
    @PostMapping("/comments/{commentId}/upvote")
    ApiResponse<CommentService.UpvoteResult> upvote(@CurrentUserId Long userId, @PathVariable Long commentId) {
        return ApiResponse.ok("댓글 공감이 처리되었습니다.", service.toggleUpvote(commentId, userId));
    }
    @DeleteMapping("/comments/{commentId}")
    ApiResponse<Void> delete(@CurrentUserId Long userId, @PathVariable Long commentId) {
        service.deleteComment(commentId, userId);
        return ApiResponse.ok("댓글이 삭제되었습니다.", null);
    }

    record CreateRequest(@NotBlank(message = "내용은 필수입니다.") @Size(max = 1000, message = "내용은 1000자 이하여야 합니다.") String content) {}
}
