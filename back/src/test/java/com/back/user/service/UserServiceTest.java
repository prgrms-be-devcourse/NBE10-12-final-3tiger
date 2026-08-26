package com.back.user.service;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.course.domain.Persona;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.dto.MyPageResponse;
import com.back.user.dto.SignupRequest;
import com.back.user.dto.SignupResponse;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
