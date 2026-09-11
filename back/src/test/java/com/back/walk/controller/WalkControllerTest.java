package com.back.walk.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.support.RateLimitWebMvcTestSupport;
import com.back.walk.dto.WalkCompletionRequest;
import com.back.walk.dto.WalkRecordResponse;
import com.back.walk.service.WalkService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalkController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class WalkControllerTest extends RateLimitWebMvcTestSupport {

    @Autowired MockMvc mvc;
    @MockitoBean WalkService service;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    void returnsFiveRecentWalksUsingPageSize() throws Exception {
        WalkRecordResponse walk = new WalkRecordResponse(
                31L, 44L, "서울숲 코스", LocalDateTime.of(2026, 9, 11, 9, 35),
                2_500, 2_100, 4
        );
        given(service.getMyWalks(7L, 0, 5))
                .willReturn(new PageResponse<>(List.of(walk), 0, 5, 1));

        mvc.perform(get("/api/v1/users/me/walks")
                        .param("page", "0")
                        .param("size", "5")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].courseName").value("서울숲 코스"))
                .andExpect(jsonPath("$.data.content[0].distanceMeters").value(2500))
                .andExpect(jsonPath("$.data.content[0].durationSeconds").value(2100))
                .andExpect(jsonPath("$.data.content[0].rating").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(service).getMyWalks(7L, 0, 5);
    }

    @Test
    void completesWalk() throws Exception {
        given(service.complete(any(), any(), any(WalkCompletionRequest.class)))
                .willReturn(new WalkRecordResponse(
                        31L, 44L, "서울숲 코스", LocalDateTime.of(2026, 9, 11, 9, 35),
                        2_500, 2_100, null
                ));

        mvc.perform(post("/api/v1/courses/44/walks")
                        .with(authenticatedAs(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startedAt": "2026-09-11T09:00:00+09:00",
                                  "finishedAt": "2026-09-11T09:35:00+09:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walkId").value(31))
                .andExpect(jsonPath("$.data.durationSeconds").value(2100));
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mvc.perform(get("/api/v1/users/me/walks")
                        .param("size", "101")
                        .with(authenticatedAs(7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me/walks"))
                .andExpect(status().isUnauthorized());
    }
}
