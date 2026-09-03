package com.back.hazard.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardConfirmationResponse;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardCreateResponse;
import com.back.hazard.dto.HazardResponse;
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
import static org.mockito.BDDMockito.willThrow;
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
    @DisplayName("인증된 사용자가 GPS를 포함해 첫 위험 신고를 등록한다")
    void createsHazardWithGps() throws Exception {
        given(hazardService.create(eq(1L), eq(10L), any(HazardCreateRequest.class)))
                .willReturn(new HazardCreateResponse(30L));

        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 10L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "그늘진 구간 결빙 주의",
                                  "latitude": 37.5219,
                                  "longitude": 126.8575
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("위험 신고가 등록되었습니다."))
                .andExpect(jsonPath("$.data.hazardId").value(30));

        verify(hazardService).create(eq(1L), eq(10L), any(HazardCreateRequest.class));
    }

    @Test
    @DisplayName("GPS가 없거나 좌표 범위를 벗어난 신고는 400이다")
    void validatesGps() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 10L)
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "결빙 주의",
                                  "longitude": 181
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(hazardService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험 신고를 등록할 수 없다")
    void rejectsUnauthenticatedCreate() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/hazards", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hazardType": "빙판",
                                  "severity": "상",
                                  "content": "결빙 주의",
                                  "latitude": 37.5219,
                                  "longitude": 126.8575
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("공개 조회는 ACTIVE Hazard와 confirmationCount를 반환한다")
    void returnsActiveHazards() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 3, 12, 0);
        LocalDateTime activatedAt = LocalDateTime.of(2026, 9, 3, 12, 30);
        given(hazardService.getActiveHazards(10L)).willReturn(List.of(
                new HazardResponse(30L, "빙판", HazardStatus.ACTIVE, 4L, createdAt, activatedAt)
        ));

        mockMvc.perform(get("/api/v1/courses/{courseId}/hazards", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].hazardId").value(30))
                .andExpect(jsonPath("$.data[0].hazardType").value("빙판"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].confirmationCount").value(4))
                .andExpect(jsonPath("$.data[0].expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].upvoteCount").doesNotExist());
    }

    @Test
    @DisplayName("인증된 사용자가 위험 존재를 한 번 확인한다")
    void confirmsHazard() throws Exception {
        given(hazardService.confirm(1L, 30L))
                .willReturn(new HazardConfirmationResponse(true, 5L));

        mockMvc.perform(post("/api/v1/hazards/{hazardId}/confirmations", 30L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("위험 확인이 반영되었습니다."))
                .andExpect(jsonPath("$.data.confirmed").value(true))
                .andExpect(jsonPath("$.data.confirmationCount").value(5));

        verify(hazardService).confirm(1L, 30L);
    }

    @Test
    @DisplayName("같은 사용자의 중복 위험 확인은 409이다")
    void rejectsDuplicateConfirmation() throws Exception {
        given(hazardService.confirm(1L, 30L)).willThrow(
                new ApiException(HttpStatus.CONFLICT, "이미 확인한 위험입니다."));

        mockMvc.perform(post("/api/v1/hazards/{hazardId}/confirmations", 30L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 확인한 위험입니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험을 확인할 수 없다")
    void rejectsUnauthenticatedConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/hazards/{hazardId}/confirmations", 30L))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("인증된 사용자가 자신의 위험 신고를 삭제한다")
    void deletesOwnHazardReport() throws Exception {
        mockMvc.perform(delete("/api/v1/hazards/{hazardId}/reports/me", 30L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("위험 신고가 삭제되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(hazardService).deleteMyReport(1L, 30L);
    }

    @Test
    @DisplayName("자신의 위험 신고가 없으면 404이다")
    void returnsNotFoundWhenOwnReportDoesNotExist() throws Exception {
        willThrow(new ApiException(HttpStatus.NOT_FOUND, "해당 위험에 등록한 신고가 없습니다."))
                .given(hazardService).deleteMyReport(1L, 30L);

        mockMvc.perform(delete("/api/v1/hazards/{hazardId}/reports/me", 30L)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 위험에 등록한 신고가 없습니다."));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 위험 신고를 삭제할 수 없다")
    void rejectsUnauthenticatedReportDeletion() throws Exception {
        mockMvc.perform(delete("/api/v1/hazards/{hazardId}/reports/me", 30L))
                .andExpect(status().isUnauthorized());

        verify(hazardService, never()).deleteMyReport(any(), any());
    }
}
