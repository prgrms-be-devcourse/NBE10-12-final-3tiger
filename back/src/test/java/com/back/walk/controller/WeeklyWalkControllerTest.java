package com.back.walk.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.support.RateLimitWebMvcTestSupport;
import com.back.walk.dto.WeeklyWalkResponse;
import com.back.walk.dto.WeeklyWalkResponse.DailyWalkRecord;
import com.back.walk.service.WeeklyWalkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WeeklyWalkController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class WeeklyWalkControllerTest extends RateLimitWebMvcTestSupport {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeeklyWalkService service;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    void returnsWeeklyWalksForAuthenticatedUser() throws Exception {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        given(service.getWeeklyWalks(7L, monday)).willReturn(new WeeklyWalkResponse(
                monday,
                LocalDate.of(2026, 9, 13),
                1,
                1_500,
                20,
                List.of(new DailyWalkRecord(monday, 1, 1_500, 20))
        ));

        mvc.perform(get("/api/v1/users/me/walks/weekly")
                        .param("weekStart", "2026-09-07")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.weekStart").value("2026-09-07"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-09-13"))
                .andExpect(jsonPath("$.data.totalWalkCount").value(1))
                .andExpect(jsonPath("$.data.totalDistanceMeters").value(1500))
                .andExpect(jsonPath("$.data.dailyRecords[0].date").value("2026-09-07"));

        verify(service).getWeeklyWalks(7L, monday);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me/walks/weekly"))
                .andExpect(status().isUnauthorized());
    }
}
