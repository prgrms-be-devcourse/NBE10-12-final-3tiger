package com.back.post.controller;

import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import com.back.post.service.PostService;
import com.back.post.storage.PhotoStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {
    private final PostService service;
    public PostController(PostService service) { this.service = service; }

    @GetMapping
    ApiResponse<PageResponse<PostService.FeedItem>> feed(@CurrentUserId(required = false) Long userId,
            @RequestParam(defaultValue = "latest") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("피드 조회 성공", service.feed(userId, sort, page, size));
    }
    @GetMapping("/me")
    ApiResponse<PageResponse<PostService.MyPostItem>> mine(@CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("내 게시글 목록 조회 성공", service.mine(userId, page, size));
    }
    @PostMapping("/photo-upload-url")
    ApiResponse<PhotoStorage.UploadTarget> uploadUrl(@CurrentUserId Long userId, @Valid @RequestBody UploadRequest request) {
        return ApiResponse.ok("업로드 URL 발급 성공", service.uploadUrl(userId, request.fileName(), request.contentType()));
    }
    @PostMapping
    ApiResponse<PostService.CreatedPost> create(@CurrentUserId Long userId, @Valid @RequestBody CreateRequest request) {
        var command = new PostService.CreateCommand(request.courseId(), request.content(), request.photoUrl(), request.walkedAt());
        return ApiResponse.ok("게시물이 등록되었습니다.", service.create(userId, command));
    }
    @DeleteMapping("/{postId}")
    ApiResponse<Void> delete(@CurrentUserId Long userId, @PathVariable Long postId) {
        service.delete(userId, postId);
        return ApiResponse.ok("게시물이 삭제되었습니다.", null);
    }

    record UploadRequest(@NotBlank(message = "파일명은 필수입니다.") String fileName,
                         @Pattern(regexp = "image/(jpeg|png|webp)", message = "지원하지 않는 이미지 형식입니다.") String contentType) {}
    record CreateRequest(@Schema(example = "1") @NotNull(message = "코스 ID는 필수입니다.") Long courseId,
                         @Schema(example = "날씨가 좋아서 즐겁게 걸었습니다.") @NotBlank(message = "내용은 필수입니다.") @Size(max = 1000, message = "내용은 1000자 이하여야 합니다.") String content,
                         @Schema(example = "https://example.com/walk.jpg") @Size(max = 2048, message = "사진 URL이 너무 깁니다.") String photoUrl,
                         @Schema(example = "2026-08-26T09:50:00") @NotNull(message = "산책 시각은 필수입니다.") LocalDateTime walkedAt) {}
}
