package com.back.walk.reservation.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.support.RateLimitWebMvcTestSupport;
import com.back.walk.reservation.domain.WalkReservationStatus;
import com.back.walk.reservation.dto.WalkReservationRequest;
import com.back.walk.reservation.dto.WalkReservationResponse;
import com.back.walk.reservation.service.WalkReservationService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalkReservationController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class WalkReservationControllerTest extends RateLimitWebMvcTestSupport {

    @Autowired MockMvc mvc;
    @MockitoBean WalkReservationService service;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    void createsReservation() throws Exception {
        given(service.reserve(any(), any(WalkReservationRequest.class)))
                .willReturn(response(WalkReservationStatus.SCHEDULED));

        mvc.perform(post("/api/v1/walk-reservations")
                        .with(authenticatedAs(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": 44,
                                  "scheduledAt": "2099-09-15T18:30:00+09:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseName").value("서울숲 코스"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void returnsUpcomingReservations() throws Exception {
        given(service.getUpcoming(7L, 0, 20))
                .willReturn(new PageResponse<>(List.of(response(WalkReservationStatus.SCHEDULED)), 0, 20, 1));

        mvc.perform(get("/api/v1/users/me/walk-reservations")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reservationId").value(13))
                .andExpect(jsonPath("$.data.content[0].scheduledAt").value("2099-09-15T18:30:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(service).getUpcoming(7L, 0, 20);
    }

    @Test
    void returnsScheduledAndCompletedReservationsForMonth() throws Exception {
        given(service.getMonthly(7L, java.time.YearMonth.of(2099, 9)))
                .willReturn(List.of(response(WalkReservationStatus.SCHEDULED)));

        mvc.perform(get("/api/v1/users/me/walk-reservations/monthly")
                        .param("yearMonth", "2099-09")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value("서울숲 코스"))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));

        verify(service).getMonthly(7L, java.time.YearMonth.of(2099, 9));
    }

    @Test
    void cancelsReservation() throws Exception {
        given(service.cancel(7L, 13L)).willReturn(response(WalkReservationStatus.CANCELED));

        mvc.perform(delete("/api/v1/walk-reservations/13")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me/walk-reservations"))
                .andExpect(status().isUnauthorized());
    }

    private WalkReservationResponse response(WalkReservationStatus status) {
        return new WalkReservationResponse(
                13L,
                44L,
                "서울숲 코스",
                LocalDateTime.of(2099, 9, 15, 18, 30),
                status
        );
    }
}
