package com.back.post.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.jwt.JwtProvider;
import com.back.post.service.PostLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.back.TestAuthentication.authenticatedAs;

@WebMvcTest(PostLikeController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class})
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostLikeService postLikeService;

    @MockitoBean
    private JwtProvider jwtProvider;

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
}
