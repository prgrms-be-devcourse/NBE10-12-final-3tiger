package com.back.user.email;

import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.support.RateLimitWebMvcTestSupport;
import com.back.user.email.controller.EmailVerificationController;
import com.back.user.email.dto.EmailVerificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailVerificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EmailVerificationControllerTest extends RateLimitWebMvcTestSupport {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmailVerificationService service;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    void sendIsPermitAllAndReturnsSuccess() throws Exception {
        willDoNothing().given(service).sendVerificationCode("user@example.com");

        mvc.perform(post("/api/v1/users/email-verifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("인증번호가 발송되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).sendVerificationCode("user@example.com");
    }

    @Test
    void verifyIsPermitAllAndReturnsVerificationToken() throws Exception {
        given(service.verifyCode("user@example.com", "123456"))
                .willReturn(new EmailVerificationResponse("verification-token"));

        mvc.perform(post("/api/v1/users/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."))
                .andExpect(jsonPath("$.data.verificationToken").value("verification-token"));
    }

    @Test
    void sendRejectsEmailWithoutTopLevelDomain() throws Exception {
        mvc.perform(post("/api/v1/users/email-verifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"test@adb"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service);
    }

    @Test
    void verifyRejectsInvalidEmailFormat() throws Exception {
        mvc.perform(post("/api/v1/users/email-verifications/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid-email","code":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service);
    }

    @Test
    void sendReturnsConflictForExistingEmail() throws Exception {
        willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS))
                .given(service).sendVerificationCode("used@example.com");

        mvc.perform(post("/api/v1/users/email-verifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"used@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409_1"));
    }
}
