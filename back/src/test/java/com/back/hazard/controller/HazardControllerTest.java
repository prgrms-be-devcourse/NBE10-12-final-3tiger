package com.back.hazard.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardCreateResponse;
import com.back.hazard.dto.HazardResponse;
import com.back.hazard.dto.HazardUpvoteResponse;
import com.back.hazard.service.HazardService;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HazardController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class HazardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private HazardService hazardService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("인증된 작성자가 자신의 위험 신고를 삭제한다")
    void deletesOwnHazard() throws Exception {
        mockMvc.perform(delete("/api/v1/hazards/{hazardId}", 10L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("위험 신고가 삭제되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(hazardService).delete(1L, 10L);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험 신고를 삭제할 수 없다")
    void rejectsUnauthenticatedDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/hazards/{hazardId}", 10L))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).delete(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 위험 신고 삭제는 404를 반환한다")
    void rejectsDeletingUnknownHazard() throws Exception {
        org.mockito.Mockito.doThrow(
                        new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 위험 신고입니다."))
                .when(hazardService).delete(1L, 999L);

        mockMvc.perform(delete("/api/v1/hazards/{hazardId}", 999L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 위험 신고입니다."));
    }

    @Test
    @DisplayName("다른 사용자의 위험 신고 삭제는 403을 반환한다")
    void rejectsDeletingOtherUsersHazard() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(
                        HttpStatus.FORBIDDEN,
                        "본인이 등록한 위험 신고만 삭제할 수 있습니다."))
                .when(hazardService).delete(1L, 10L);

        mockMvc.perform(delete("/api/v1/hazards/{hazardId}", 10L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("본인이 등록한 위험 신고만 삭제할 수 있습니다."));
    }

    @Test
    @DisplayName("인증된 사용자가 위험 신고에 공감한다")
    void upvotesHazard() throws Exception {
        given(hazardService.upvote(10L)).willReturn(new HazardUpvoteResponse(true, 13));

        mockMvc.perform(post("/api/v1/hazards/{hazardId}/upvote", 10L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("공감이 반영되었습니다."))
                .andExpect(jsonPath("$.data.upvoted").value(true))
                .andExpect(jsonPath("$.data.upvoteCount").value(13));

        verify(hazardService).upvote(10L);
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험 신고에 공감할 수 없다")
    void rejectsUnauthenticatedUpvote() throws Exception {
        mockMvc.perform(post("/api/v1/hazards/{hazardId}/upvote", 10L))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).upvote(any());
    }

    @Test
    @DisplayName("존재하지 않는 위험 신고에 공감하면 404를 반환한다")
    void rejectsUpvoteForUnknownHazard() throws Exception {
        given(hazardService.upvote(999L))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 위험 신고입니다."));

        mockMvc.perform(post("/api/v1/hazards/{hazardId}/upvote", 999L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 위험 신고입니다."));
    }

    @Test
    @DisplayName("인증된 사용자가 코스 위험 신고를 등록한다")
    void createsHazard() throws Exception {
        given(hazardService.create(eq(1L), eq(1L), any(HazardCreateRequest.class)))
                .willReturn(new HazardCreateResponse(30L));

        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 1L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "그늘진 구간 결빙 주의",
                                  "expiresAt": "2027-03-31T00:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("위험 신고가 등록되었습니다."))
                .andExpect(jsonPath("$.data.hazardId").value(30));

        verify(hazardService).create(eq(1L), eq(1L), any(HazardCreateRequest.class));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험 신고를 등록할 수 없다")
    void rejectsUnauthenticatedCreate() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "그늘진 구간 결빙 주의",
                                  "expiresAt": "2027-03-31T00:00:00"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 코스에 위험 신고를 등록하면 404를 반환한다")
    void rejectsCreateForUnknownCourse() throws Exception {
        given(hazardService.create(eq(1L), eq(999L), any(HazardCreateRequest.class)))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));

        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 999L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "그늘진 구간 결빙 주의",
                                  "expiresAt": "2027-03-31T00:00:00"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 코스입니다."));
    }

    @Test
    @DisplayName("필수 문자열이 비어 있거나 만료 시각이 없으면 400을 반환한다")
    void rejectsInvalidCreateBody() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 1L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": " ",
                                  "severity": "",
                                  "content": null
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(hazardService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("Entity 및 DB 제한보다 긴 문자열이면 400을 반환한다")
    void rejectsTooLongCreateBody() throws Exception {
        String hazardType = "가".repeat(51);

        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 1L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "%s",
                                  "severity": "상",
                                  "content": "그늘진 구간 결빙 주의",
                                  "expiresAt": "2027-03-31T00:00:00"
                                }
                                """.formatted(hazardType)))
                .andExpect(status().isBadRequest());

        verify(hazardService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("인증 없이 코스의 활성 위험 신고 목록을 조회한다")
    void returnsActiveHazards() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 1, 18, 0);
        given(hazardService.getActiveHazards(1L)).willReturn(List.of(
                new HazardResponse(10L, "CONSTRUCTION", "HIGH", "보도 공사 중입니다.", 3, expiresAt)
        ));

        mockMvc.perform(get("/api/v1/courses/{courseId}/hazards", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("코스 위험 신고 조회 성공"))
                .andExpect(jsonPath("$.data[0].hazardId").value(10))
                .andExpect(jsonPath("$.data[0].hazardType").value("CONSTRUCTION"))
                .andExpect(jsonPath("$.data[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data[0].content").value("보도 공사 중입니다."))
                .andExpect(jsonPath("$.data[0].upvoteCount").value(3))
                .andExpect(jsonPath("$.data[0].expiresAt").value("2026-09-01T18:00:00"));

        verify(hazardService).getActiveHazards(1L);
    }

    @Test
    @DisplayName("활성 위험 신고가 없으면 빈 배열을 반환한다")
    void returnsEmptyArray() throws Exception {
        given(hazardService.getActiveHazards(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/courses/{courseId}/hazards", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 코스의 위험 신고 조회는 404를 반환한다")
    void returnsNotFoundForUnknownCourse() throws Exception {
        given(hazardService.getActiveHazards(999L))
                .willThrow(new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));

        mockMvc.perform(get("/api/v1/courses/{courseId}/hazards", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 코스입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
