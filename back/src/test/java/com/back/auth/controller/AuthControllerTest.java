package com.back.auth.controller;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
import com.back.auth.dto.SignupRequest;
import com.back.auth.service.AuthService;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;

    @Test
    void signup_성공_201() throws Exception {
        given(authService.signup(anyString(), anyString(), anyString()))
                .willReturn(new AuthResponse("at", "rt"));

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("test@test.com", "password123", "닉네임"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("at"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt"));
    }

    @Test
    void signup_이메일중복_409() throws Exception {
        given(authService.signup(anyString(), anyString(), anyString()))
                .willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("dupe@test.com", "password123", "닉네임"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409_1"));
    }

    @Test
    void signup_유효성실패_400() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("not-an-email", "short", "닉"))))
                .andExpect(status().isBadRequest());
    }

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
}