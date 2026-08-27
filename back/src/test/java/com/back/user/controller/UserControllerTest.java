package com.back.user.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.course.domain.Persona;
import com.back.user.dto.MyPageUpdateRequest;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.ProfileImageResponse;
import com.back.user.dto.SignupResponse;
import com.back.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    void updateMyPageUpdatesAllFields() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "새 닉네임",
                                  "primaryPersona": "senior",
                                  "personaTags": ["senior", "stroller"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("마이페이지 수정 성공"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(userService).updateMyPage(
                1L,
                new MyPageUpdateRequest(
                        "새 닉네임",
                        Persona.senior,
                        List.of(Persona.senior, Persona.stroller)
                )
        );
    }

    @Test
    void updateMyPageAcceptsPartialRequest() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "새 닉네임"}
                                """))
                .andExpect(status().isOk());

        verify(userService).updateMyPage(
                1L,
                new MyPageUpdateRequest("새 닉네임", null, null)
        );
    }

    @Test
    void updateMyPageAcceptsEmptyRequest() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(userService).updateMyPage(1L, new MyPageUpdateRequest(null, null, null));
    }

    @Test
    void updateMyPageRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456789012345678901234567890123456789012345678901"})
    void updateMyPageRejectsInvalidNickname(String nickname) throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateMyPageRejectsInvalidPersona() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primaryPersona": "SENIOR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateMyPageAcceptsEmptyPersonaTags() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personaTags": []}
                                """))
                .andExpect(status().isOk());

        verify(userService).updateMyPage(1L, new MyPageUpdateRequest(null, null, List.of()));
    }

    @Test
    void updateMyPageRejectsNullPersonaTag() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personaTags": ["senior", null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileImageReturnsUploadedImageUrl() throws Exception {
        MockMultipartFile file = imageFile("profile.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String profileImageUrl = "https://cdn.example.com/profile-images/1/profile.jpg";
        given(userService.updateProfileImage(eq(1L), any()))
                .willReturn(new ProfileImageResponse(profileImageUrl));

        mvc.perform(multipart("/api/v1/users/me/profile-image")
                        .file(file)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("프로필 사진이 변경되었습니다."))
                .andExpect(jsonPath("$.data.profileImageUrl").value(profileImageUrl));

        verify(userService).updateProfileImage(eq(1L), any());
    }

    @Test
    void updateProfileImageRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/profile-image")
                        .file(imageFile("profile.jpg", "image/jpeg", new byte[]{1})))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileImageRejectsMissingFilePart() throws Exception {
        given(userService.updateProfileImage(1L, null))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mvc.perform(multipart("/api/v1/users/me/profile-image")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "unsupported", "oversized"})
    void updateProfileImageRejectsInvalidFile(String caseName) throws Exception {
        MockMultipartFile file = switch (caseName) {
            case "empty" -> imageFile("profile.jpg", "image/jpeg", new byte[0]);
            case "unsupported" -> imageFile("profile.gif", "image/gif", new byte[]{1});
            case "oversized" -> imageFile(
                    "profile.jpg",
                    "image/jpeg",
                    new byte[10 * 1024 * 1024 + 1]
            );
            default -> throw new IllegalArgumentException(caseName);
        };
        given(userService.updateProfileImage(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mvc.perform(multipart("/api/v1/users/me/profile-image")
                        .file(file)
                        .with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void withdrawReturnsSuccessForAuthenticatedUser() throws Exception {
        mvc.perform(patch("/api/v1/users/withdraw")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 정상적으로 완료되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(userService).withdraw(1L);
    }

    @Test
    void withdrawRejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(patch("/api/v1/users/withdraw"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    private MockMultipartFile imageFile(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", fileName, contentType, content);
    }
}
