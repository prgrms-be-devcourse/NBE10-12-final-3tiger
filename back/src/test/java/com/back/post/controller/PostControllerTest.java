package com.back.post.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.post.service.PostService;
import com.back.post.storage.PhotoStorage;
import com.back.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class PostControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PostService postService;
    @MockitoBean JwtProvider jwtProvider;

    @Test
    @DisplayName("피드는 인증 없이 내용·좋아요·댓글 정보를 조회할 수 있다")
    void feed() throws Exception {
        var item = new PostService.FeedItem(10L, 1L, "산책러", "좋은 산책이었습니다.",
                "https://example.com/walk.jpg", 3, 2, false, false, false,
                LocalDateTime.of(2026, 8, 26, 9, 0));
        given(postService.feed(null, "latest", 0, 20))
                .willReturn(new PageResponse<>(List.of(item), 0, 20, 1));

        mvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value(10))
                .andExpect(jsonPath("$.data.content[0].content").value("좋은 산책이었습니다."))
                .andExpect(jsonPath("$.data.content[0].likeCount").value(3))
                .andExpect(jsonPath("$.data.content[0].commentCount").value(2))
                .andExpect(jsonPath("$.data.content[0].isLiked").value(false))
                .andExpect(jsonPath("$.data.content[0].isBookmarked").value(false))
                .andExpect(jsonPath("$.data.content[0].isMine").value(false));
    }

    @Test
    @DisplayName("인증 사용자는 코스 1에 내용이 있는 게시물을 작성할 수 있다")
    void create() throws Exception {
        given(postService.create(eq(1L), any(PostService.CreateCommand.class)))
                .willReturn(new PostService.CreatedPost(10L));

        mvc.perform(post("/api/v1/posts")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"content":"좋은 산책이었습니다.",
                                 "photoUrl":"https://example.com/walk.jpg","walkedAt":"2026-08-26T09:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(10));

        verify(postService).create(eq(1L), any(PostService.CreateCommand.class));
    }

    @Test
    @DisplayName("인증 없이 게시물을 작성하면 401을 반환한다")
    void createRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"content":"내용","walkedAt":"2026-08-26T09:00:00"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(postService, never()).create(any(), any());
    }

    @Test
    @DisplayName("내용이 비어 있으면 400을 반환한다")
    void validatesContent() throws Exception {
        mvc.perform(post("/api/v1/posts")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":1,"content":"","walkedAt":"2026-08-26T09:00:00"}
                                """))
                .andExpect(status().isBadRequest());

        verify(postService, never()).create(any(), any());
    }

    @Test
    @DisplayName("사진 업로드 대상 URL 발급 결과를 반환한다")
    void uploadUrl() throws Exception {
        given(postService.uploadUrl(1L, "walk.jpg", "image/jpeg"))
                .willReturn(new PhotoStorage.UploadTarget("upload-url", "photo-url", 300));

        mvc.perform(post("/api/v1/posts/photo-upload-url")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"walk.jpg\",\"contentType\":\"image/jpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("upload-url"))
                .andExpect(jsonPath("$.data.photoUrl").value("photo-url"))
                .andExpect(jsonPath("$.data.expireInSeconds").value(300));

        verify(postService).uploadUrl(1L, "walk.jpg", "image/jpeg");
    }

    @Test
    @DisplayName("지원하지 않는 사진 형식은 업로드 URL을 발급하지 않는다")
    void rejectsUnsupportedPhotoContentType() throws Exception {
        mvc.perform(post("/api/v1/posts/photo-upload-url")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"walk.pdf\",\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).uploadUrl(any(), any(), any());
    }

    @Test
    @DisplayName("사진 형식이 누락되면 업로드 URL을 발급하지 않는다")
    void requiresPhotoContentType() throws Exception {
        mvc.perform(post("/api/v1/posts/photo-upload-url")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"walk.jpg\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).uploadUrl(any(), any(), any());
    }

    @Test
    @DisplayName("인증 사용자는 자신의 게시물을 삭제할 수 있다")
    void deletePost() throws Exception {
        mvc.perform(delete("/api/v1/posts/{postId}", 10L).with(authenticatedAs(1L)))
                .andExpect(status().isOk());

        verify(postService).delete(1L, 10L);
    }
}
