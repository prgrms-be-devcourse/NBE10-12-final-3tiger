package com.back.comment.controller;

import com.back.comment.service.CommentService;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.back.TestAuthentication.authenticatedAs;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("t1: GET /api/v1/posts/{postId}/comments 요청 시 200과 페이징 응답을 반환한다")
    void t1() throws Exception {
        // given
        CommentService.CommentResponse commentResponse =
                new CommentService.CommentResponse(1L, 1L, "산책러", "좋은 코스네요", 0, LocalDateTime.now());
        given(commentService.getComments(eq(1L), any(Pageable.class)))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(commentResponse))));

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentId").value(1))
                .andExpect(jsonPath("$.data.content[0].nickname").value("산책러"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("t2: POST /api/v1/posts/{postId}/comments 요청 시 200과 commentId를 반환한다")
    void t2() throws Exception {
        // given
        given(commentService.createComment(1L, 1L, "좋은 코스네요")).willReturn(10L);

        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", 1L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"좋은 코스네요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(10));
    }

    @Test
    @DisplayName("t3: 빈 content로 댓글 작성 시 검증 실패로 400을 반환한다")
    void t3() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", 1L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("t4: POST /api/v1/comments/{commentId}/upvote 요청 시 200과 공감 결과를 반환한다")
    void t4() throws Exception {
        // given
        given(commentService.toggleUpvote(1L, 1L)).willReturn(new CommentService.UpvoteResult(true, 3));

        // when & then
        mockMvc.perform(post("/api/v1/comments/{commentId}/upvote", 1L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upvoted").value(true))
                .andExpect(jsonPath("$.data.upvoteCount").value(3));
    }

    @Test
    @DisplayName("t5: DELETE /api/v1/comments/{commentId} 요청 시 200을 반환한다")
    void t5() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/comments/{commentId}", 1L).with(authenticatedAs(1L)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("t6: 미인증 상태로 댓글 작성 시 401을 반환한다")
    void t6() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"좋은 코스네요\"}"))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).createComment(any(), any(), any());
    }

    @Test
    @DisplayName("t7: 존재하지 않는 postId로 댓글 작성 시 404를 반환한다")
    void t7() throws Exception {
        // given
        given(commentService.createComment(999L, 1L, "좋은 코스네요"))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));

        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", 999L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"좋은 코스네요\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("t8: 본인 댓글이 아닌 댓글 삭제 시 403을 반환한다")
    void t8() throws Exception {
        // given
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다."))
                .when(commentService).deleteComment(1L, 2L);

        // when & then
        mockMvc.perform(delete("/api/v1/comments/{commentId}", 1L).with(authenticatedAs(2L)))
                .andExpect(status().isForbidden());
    }
}
