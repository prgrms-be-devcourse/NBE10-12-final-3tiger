package com.back.user.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.SignupResponse;
import com.back.user.dto.UpdateProfileRequest;
import com.back.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void signupReturnsCreatedUserWithoutPassword() throws Exception {
        given(userService.signup(any())).willReturn(
                new SignupResponse(1L, "NORMAL")
        );

        mvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "walker@example.com",
                                  "password": "plain-password",
                                  "nickname": "산책러"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.loginType").value("NORMAL"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"\",\"password\":\"plain-password\",\"nickname\":\"산책러\"}",
            "{\"email\":\"invalid-email\",\"password\":\"plain-password\",\"nickname\":\"산책러\"}",
            "{\"email\":\"walker@example.com\",\"password\":\"\",\"nickname\":\"산책러\"}",
            "{\"email\":\"walker@example.com\",\"password\":\"plain-password\",\"nickname\":\"\"}"
    })
    void signupRejectsInvalidRequest(String requestBody) throws Exception {
        mvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void signupRejectsExistingEmail() throws Exception {
        given(userService.signup(any()))
                .willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "walker@example.com",
                                  "password": "plain-password",
                                  "nickname": "산책러"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409_1"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    void checkEmailReturnsAvailableResponse() throws Exception {
        String email = "available@example.com";

        mvc.perform(get("/api/v1/users/check-email")
                        .param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("사용 가능한 이메일입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(userService).checkEmailAvailability(email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-email"})
    void checkEmailRejectsInvalidEmail(String email) throws Exception {
        mvc.perform(get("/api/v1/users/check-email")
                        .param("email", email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void checkEmailRejectsMissingEmail() throws Exception {
        mvc.perform(get("/api/v1/users/check-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void checkEmailRejectsExistingEmail() throws Exception {
        String email = "used@example.com";
        willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS))
                .given(userService)
                .checkEmailAvailability(email);

        mvc.perform(get("/api/v1/users/check-email")
                        .param("email", email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_409_1"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
    }

    @Test
    void getMyPageReturnsCurrentUserProfile() throws Exception {
        given(userService.getMyPage(1L)).willReturn(
                new MyPageResponse(
                        1L,
                        "산책러",
                        "walker@example.com",
                        "KAKAO",
                        "https://k.kakaocdn.net/profile.jpg",
                        "dog",
                        List.of("dog", "senior")
                )
        );

        mvc.perform(get("/api/v1/users/me")
                .with(authenticatedAs(1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("마이페이지 조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("산책러"))
                .andExpect(jsonPath("$.data.email").value("walker@example.com"))
                .andExpect(jsonPath("$.data.loginType").value("KAKAO"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://k.kakaocdn.net/profile.jpg"))
                .andExpect(jsonPath("$.data.primaryPersona").value("dog"))
                .andExpect(jsonPath("$.data.personaTags[0]").value("dog"))
                .andExpect(jsonPath("$.data.personaTags[1]").value("senior"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.providerUid").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist());
    }

    @Test
    void getMyPageRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMyProfileSucceedsWithValidPayload() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "새 닉네임",
                                  "primaryPersona": "dog",
                                  "personaTags": ["dog", "senior"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("프로필이 수정되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        ArgumentCaptor<UpdateProfileRequest> captor =
                ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(userService).updateProfile(eq(1L), captor.capture());
        UpdateProfileRequest sent = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(sent.nickname()).isEqualTo("새 닉네임");
        org.assertj.core.api.Assertions.assertThat(sent.primaryPersona()).isEqualTo("dog");
        org.assertj.core.api.Assertions.assertThat(sent.personaTags())
                .containsExactly("dog", "senior");
    }

    @Test
    void updateMyProfileRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "새 닉네임",
                                  "primaryPersona": "dog",
                                  "personaTags": []
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nickname\":\"\",\"primaryPersona\":\"dog\",\"personaTags\":[]}",
            "{\"nickname\":\"   \",\"primaryPersona\":\"dog\",\"personaTags\":[]}",
            "{\"primaryPersona\":\"dog\",\"personaTags\":[]}"
    })
    void updateMyProfileRejectsBlankNickname(String requestBody) throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void updateMyProfileRejectsNicknameOver50Chars() throws Exception {
        String longNickname = "가".repeat(51);
        String body = "{\"nickname\":\"" + longNickname + "\",\"primaryPersona\":\"dog\",\"personaTags\":[]}";

        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}
