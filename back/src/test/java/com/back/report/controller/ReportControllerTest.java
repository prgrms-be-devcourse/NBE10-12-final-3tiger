package com.back.report.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.report.domain.ReportReason;
import com.back.report.domain.ReportTargetType;
import com.back.report.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("t1: POST /api/v1/posts/{postId}/reports 요청 시 200과 신고 결과를 반환한다")
    void t1() throws Exception {
        given(reportService.report(1L, ReportTargetType.POST, 7L, ReportReason.SPAM))
                .willReturn(new ReportService.ReportResult(ReportTargetType.POST, 7L, 3L, true));

        mockMvc.perform(post("/api/v1/posts/{postId}/reports", 7L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportCount").value(3))
                .andExpect(jsonPath("$.data.hidden").value(true));

        verify(reportService).report(1L, ReportTargetType.POST, 7L, ReportReason.SPAM);
    }

    @Test
    @DisplayName("t2: POST /api/v1/comments/{commentId}/reports 요청 시 COMMENT 타입으로 서비스를 호출한다")
    void t2() throws Exception {
        given(reportService.report(any(), any(), any(), any()))
                .willReturn(new ReportService.ReportResult(ReportTargetType.COMMENT, 5L, 1L, false));

        mockMvc.perform(post("/api/v1/comments/{commentId}/reports", 5L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ABUSE\"}"))
                .andExpect(status().isOk());

        verify(reportService).report(1L, ReportTargetType.COMMENT, 5L, ReportReason.ABUSE);
    }

    @Test
    @DisplayName("t3: POST /api/v1/users/{userId}/reports 요청 시 USER 타입으로 서비스를 호출한다")
    void t3() throws Exception {
        given(reportService.report(any(), any(), any(), any()))
                .willReturn(new ReportService.ReportResult(ReportTargetType.USER, 2L, 1L, false));

        mockMvc.perform(post("/api/v1/users/{userId}/reports", 2L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"HARASSMENT\"}"))
                .andExpect(status().isOk());

        verify(reportService).report(1L, ReportTargetType.USER, 2L, ReportReason.HARASSMENT);
    }

    @Test
    @DisplayName("t4: reason이 없으면 400을 반환하고 서비스를 호출하지 않는다")
    void t4() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/reports", 7L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(reportService, never()).report(any(), any(), any(), any());
    }

    @Test
    @DisplayName("t5: 미인증 상태로 신고 시 401을 반환한다")
    void t5() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/reports", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());

        verify(reportService, never()).report(any(), any(), any(), any());
    }

    @Test
    @DisplayName("t6: 존재하지 않는 게시물 신고 시 404를 반환한다")
    void t6() throws Exception {
        given(reportService.report(any(), any(), any(), any()))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));

        mockMvc.perform(post("/api/v1/posts/{postId}/reports", 999L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("t7: 본인 게시물 신고 시 400을 반환한다")
    void t7() throws Exception {
        given(reportService.report(any(), any(), any(), any()))
                .willThrow(new ApiException(HttpStatus.BAD_REQUEST, "본인 게시물은 신고할 수 없습니다."));

        mockMvc.perform(post("/api/v1/posts/{postId}/reports", 7L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("t8: 본인 댓글 신고 시 400을 반환한다")
    void t8() throws Exception {
        given(reportService.report(any(), any(), any(), any()))
                .willThrow(new ApiException(HttpStatus.BAD_REQUEST, "본인 댓글은 신고할 수 없습니다."));

        mockMvc.perform(post("/api/v1/comments/{commentId}/reports", 5L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ABUSE\"}"))
                .andExpect(status().isBadRequest());
    }
}
