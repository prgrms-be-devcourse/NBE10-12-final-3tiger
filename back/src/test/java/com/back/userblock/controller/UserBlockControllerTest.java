package com.back.userblock.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.userblock.service.UserBlockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBlockController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class UserBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserBlockService userBlockService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("t1: PUT /api/v1/users/{userId}/block 요청 시 200과 차단 결과를 반환한다")
    void t1() throws Exception {
        given(userBlockService.block(1L, 2L)).willReturn(new UserBlockService.BlockResult(2L, true));

        mockMvc.perform(put("/api/v1/users/{userId}/block", 2L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockedUserId").value(2))
                .andExpect(jsonPath("$.data.blocked").value(true));

        verify(userBlockService).block(1L, 2L);
    }

    @Test
    @DisplayName("t2: DELETE /api/v1/users/{userId}/block 요청 시 200과 해제 결과를 반환한다")
    void t2() throws Exception {
        given(userBlockService.unblock(1L, 2L)).willReturn(new UserBlockService.BlockResult(2L, false));

        mockMvc.perform(delete("/api/v1/users/{userId}/block", 2L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false));

        verify(userBlockService).unblock(1L, 2L);
    }

    @Test
    @DisplayName("t3: GET /api/v1/users/blocks 요청 시 200과 차단 목록을 반환한다")
    void t3() throws Exception {
        var item = new UserBlockService.BlockedUser(2L, "차단이", "https://cdn.example.com/b.jpg",
                LocalDateTime.of(2026, 9, 1, 12, 0));
        given(userBlockService.myBlocks(1L, 0, 20))
                .willReturn(new PageResponse<>(List.of(item), 0, 20, 1));

        mockMvc.perform(get("/api/v1/users/blocks").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userId").value(2))
                .andExpect(jsonPath("$.data.content[0].nickname").value("차단이"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("t4: 미인증 상태로 차단 요청 시 401을 반환한다")
    void t4() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/block", 2L))
                .andExpect(status().isUnauthorized());

        verify(userBlockService, never()).block(any(), any());
    }
}
