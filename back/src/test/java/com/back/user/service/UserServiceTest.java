package com.back.user.service;

import com.back.auth.service.AuthService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.course.domain.Persona;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.dto.MyPageUpdateRequest;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.ProfileImageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.repository.UserRepository;
import com.back.user.storage.ProfileImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @Mock
    private ProfileImageStorage profileImageStorage;

    @InjectMocks
    private UserService userService;

    @Test
    void signupEncodesPasswordAndSavesLocalUser() {
        SignupRequest request = new SignupRequest(
                "walker@example.com",
                "plain-password",
                "산책러"
        );
        User savedUser = org.mockito.Mockito.mock(User.class);

        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(savedUser.getId()).willReturn(1L);
        given(savedUser.getProvider()).willReturn(Provider.LOCAL);

        SignupResponse response = userService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User userToSave = userCaptor.getValue();

        assertThat(userToSave.getEmail()).isEqualTo(request.email());
        assertThat(userToSave.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userToSave.getNickname()).isEqualTo(request.nickname());
        assertThat(userToSave.getProvider()).isEqualTo(Provider.LOCAL);
        assertThat(userToSave.getProviderUid()).isNull();
        assertThat(response).isEqualTo(new SignupResponse(1L, "NORMAL"));
    }

    @Test
    void signupRejectsExistingEmail() {
        SignupRequest request = new SignupRequest(
                "walker@example.com",
                "plain-password",
                "산책러"
        );
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void checkEmailAvailabilityCompletesWhenEmailIsAvailable() {
        String email = "available@example.com";
        given(userRepository.existsByEmail(email)).willReturn(false);

        assertThatCode(() -> userService.checkEmailAvailability(email))
                .doesNotThrowAnyException();

        verify(userRepository).existsByEmail(email);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void checkEmailAvailabilityRejectsExistingEmail() {
        String email = "used@example.com";
        given(userRepository.existsByEmail(email)).willReturn(true);

        assertThatThrownBy(() -> userService.checkEmailAvailability(email))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository).existsByEmail(email);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getMyPageReturnsActiveUserInformation() {
        Long userId = 1L;
        User user = org.mockito.Mockito.mock(User.class);

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(user.getId()).willReturn(userId);
        given(user.getNickname()).willReturn("산책러");
        given(user.getEmail()).willReturn("walker@example.com");
        given(user.getProvider()).willReturn(Provider.KAKAO);
        given(user.getProfileImageUrl()).willReturn("https://k.kakaocdn.net/profile.jpg");
        given(user.getPrimaryPersona()).willReturn(Persona.dog);
        given(user.getPersonaTags()).willReturn(List.of(Persona.dog, Persona.senior));

        MyPageResponse response = userService.getMyPage(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.nickname()).isEqualTo("산책러");
        assertThat(response.email()).isEqualTo("walker@example.com");
        assertThat(response.loginType()).isEqualTo("KAKAO");
        assertThat(response.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/profile.jpg");
        assertThat(response.primaryPersona()).isEqualTo("dog");
        assertThat(response.personaTags()).containsExactly("dog", "senior");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void getMyPageRejectsMissingOrWithdrawnUser() {
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyPage(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMyPageUpdatesAllFields() {
        Long userId = 1L;
        User user = User.createLocal("walker@example.com", "encoded-password", "기존 닉네임");
        MyPageUpdateRequest request = new MyPageUpdateRequest(
                "새 닉네임",
                Persona.senior,
                List.of(Persona.senior, Persona.stroller)
        );
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.updateMyPage(userId, request);

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getPrimaryPersona()).isEqualTo(Persona.senior);
        assertThat(user.getPersonaTags()).containsExactly(Persona.senior, Persona.stroller);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMyPageUpdatesOnlyNicknameAndKeepsPersona() {
        Long userId = 1L;
        User user = userWithProfile();
        MyPageUpdateRequest request = new MyPageUpdateRequest("새 닉네임", null, null);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.updateMyPage(userId, request);

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getPrimaryPersona()).isEqualTo(Persona.dog);
        assertThat(user.getPersonaTags()).containsExactly(Persona.dog, Persona.walker);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMyPageUpdatesPersonaAndKeepsNickname() {
        Long userId = 1L;
        User user = userWithProfile();
        MyPageUpdateRequest request = new MyPageUpdateRequest(
                null,
                Persona.senior,
                List.of(Persona.senior, Persona.stroller)
        );
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.updateMyPage(userId, request);

        assertThat(user.getNickname()).isEqualTo("기존 닉네임");
        assertThat(user.getPrimaryPersona()).isEqualTo(Persona.senior);
        assertThat(user.getPersonaTags()).containsExactly(Persona.senior, Persona.stroller);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMyPageKeepsPersonaTagsWhenTheyAreNull() {
        Long userId = 1L;
        User user = userWithProfile();
        MyPageUpdateRequest request = new MyPageUpdateRequest(null, Persona.senior, null);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.updateMyPage(userId, request);

        assertThat(user.getPrimaryPersona()).isEqualTo(Persona.senior);
        assertThat(user.getPersonaTags()).containsExactly(Persona.dog, Persona.walker);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMyPageClearsPersonaTagsWithEmptyList() {
        Long userId = 1L;
        User user = userWithProfile();
        MyPageUpdateRequest request = new MyPageUpdateRequest(null, null, List.of());
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.updateMyPage(userId, request);

        assertThat(user.getPersonaTags()).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMyPageRejectsMissingOrWithdrawnUser() {
        Long userId = 1L;
        MyPageUpdateRequest request = new MyPageUpdateRequest("새 닉네임", null, null);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMyPage(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateProfileImageUploadsFileAndChangesUserProfileImage() {
        Long userId = 1L;
        User user = User.createLocal("walker@example.com", "encoded-password", "산책러");
        MockMultipartFile file = imageFile("profile.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String profileImageUrl = "https://cdn.example.com/profile-images/1/profile.jpg";
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(profileImageStorage.upload(userId, file)).willReturn(profileImageUrl);

        ProfileImageResponse response = userService.updateProfileImage(userId, file);

        assertThat(response.profileImageUrl()).isEqualTo(profileImageUrl);
        assertThat(user.getProfileImageUrl()).isEqualTo(profileImageUrl);
        verify(profileImageStorage).upload(userId, file);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileImageRejectsMissingOrWithdrawnUserBeforeUpload() {
        Long userId = 1L;
        MockMultipartFile file = imageFile("profile.jpg", "image/jpeg", new byte[]{1});
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfileImage(userId, file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(profileImageStorage);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileImageRejectsNullFile() {
        assertInvalidProfileImage(null);
    }

    @Test
    void updateProfileImageRejectsEmptyFile() {
        assertInvalidProfileImage(imageFile("profile.jpg", "image/jpeg", new byte[0]));
    }

    @Test
    void updateProfileImageRejectsUnsupportedContentType() {
        assertInvalidProfileImage(imageFile("profile.gif", "image/gif", new byte[]{1}));
    }

    @Test
    void updateProfileImageRejectsFileLargerThanTenMegabytes() {
        assertInvalidProfileImage(imageFile(
                "profile.jpg",
                "image/jpeg",
                new byte[10 * 1024 * 1024 + 1]
        ));
    }

    @Test
    void withdrawSoftDeletesActiveUserWithoutExplicitSave() {
        Long userId = 1L;
        User user = User.createLocal("walker@example.com", "encoded-password", "산책러");
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        userService.withdraw(userId);

        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).findByIdAndDeletedAtIsNull(userId);
        verify(authService).revokeAllRefreshTokens(userId);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void withdrawRejectsMissingUser() {
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findByIdAndDeletedAtIsNull(userId);
        verifyNoInteractions(authService);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void withdrawRejectsAlreadyWithdrawnUser() {
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository).findByIdAndDeletedAtIsNull(userId);
        verifyNoInteractions(authService);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    private User userWithProfile() {
        User user = User.createLocal("walker@example.com", "encoded-password", "기존 닉네임");
        user.updateProfile("기존 닉네임", Persona.dog, List.of(Persona.dog, Persona.walker));
        return user;
    }

    private void assertInvalidProfileImage(MockMultipartFile file) {
        Long userId = 1L;
        User user = User.createLocal("walker@example.com", "encoded-password", "산책러");
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateProfileImage(userId, file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(profileImageStorage);
        verify(userRepository, never()).save(any());
    }

    private MockMultipartFile imageFile(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", fileName, contentType, content);
    }
}
