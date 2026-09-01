package com.back.post.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.post.service.PostLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.back.TestAuthentication.authenticatedAs;

@WebMvcTest(PostLikeController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostLikeService postLikeService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("t1: PUT /api/v1/posts/{postId}/likes 요청 시 200과 좋아요 결과를 반환한다")
    void t1() throws Exception {
        // given
        given(postLikeService.like(1L, 1L)).willReturn(new PostLikeService.LikeResult(true, 5));

        // when & then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", 1L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLiked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(5));
    }

    @Test
    @DisplayName("t2: DELETE /api/v1/posts/{postId}/likes 요청 시 200과 좋아요 취소 결과를 반환한다")
    void t2() throws Exception {
        // given
        given(postLikeService.unlike(1L, 1L)).willReturn(new PostLikeService.LikeResult(false, 4));

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", 1L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLiked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(4));
    }

    @Test
    @DisplayName("t3: GET /api/v1/users/me/likes 요청 시 200과 내 좋아요 목록을 반환한다")
    void t3() throws Exception {
        // given
        var item = new PostLikeService.LikedPostItem(10L, 1L, "산책러", "좋은 산책이었습니다.",
                "https://example.com/walk.jpg", 5, 2L, true, false, LocalDateTime.of(2026, 8, 26, 9, 0));
        given(postLikeService.myLikes(1L, 0, 20))
                .willReturn(new PageResponse<>(List.of(item), 0, 20, 1));

        // when & then
        mockMvc.perform(get("/api/v1/users/me/likes").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value(10))
                .andExpect(jsonPath("$.data.content[0].likeCount").value(5))
                .andExpect(jsonPath("$.data.content[0].isBookmarked").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("t4: 미인증 상태로 좋아요 시도 시 401을 반환한다")
    void t4() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", 1L))
                .andExpect(status().isUnauthorized());

        verify(postLikeService, never()).like(any(), any());
    }

    @Test
    @DisplayName("t5: 존재하지 않는 postId로 좋아요 시도 시 404를 반환한다")
    void t5() throws Exception {
        // given
        given(postLikeService.like(999L, 1L))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));

        // when & then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", 999L).with(authenticatedAs(1L)))
                .andExpect(status().isNotFound());
    }
}
