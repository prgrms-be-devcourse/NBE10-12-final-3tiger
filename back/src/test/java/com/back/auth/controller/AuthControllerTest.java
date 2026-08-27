package com.back.auth.controller;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
import com.back.auth.dto.LogoutRequest;
import com.back.auth.service.AuthService;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.PasswordEncoderConfig;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class, PasswordEncoderConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQtdmFsdWUtZm9yLWp3dC10ZXN0aW5nLW9ubHktMjAyNCEh",
        "app.jwt.access-token-expiry=1800",
        "app.jwt.refresh-token-expiry=1209600"
})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void login_성공_200() throws Exception {
        given(authService.login(anyString(), anyString()))
                .willReturn(new AuthResponse("at", "rt"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@test.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("at"));
    }

    @Test
    void login_잘못된자격증명_401() throws Exception {
        given(authService.login(anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@test.com", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_1"));
    }

    @Test
    void login_소셜유저_409() throws Exception {
        given(authService.login(anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.SOCIAL_LOGIN_REQUIRED));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("kakao@test.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409_2"));
    }

    @Test
    void logout_성공_200() throws Exception {
        willDoNothing().given(authService).logout(anyString());

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LogoutRequest("some-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃이 완료되었습니다."));
    }

    @Test
    void logout_빈토큰_400() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LogoutRequest(""))))
                .andExpect(status().isBadRequest());
    }
}
