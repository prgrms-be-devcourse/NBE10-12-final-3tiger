package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.jwt.JwtProvider;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    void signup_성공() {
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        User savedUser = mock(User.class);
        given(savedUser.getId()).willReturn(1L);
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.signup("test@test.com", "password123", "닉네임");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.refreshToken()).isEqualTo("rt");
        verify(valueOps).set("RT:1:jti-uuid", "1", 1209600L, TimeUnit.SECONDS);
    }

    @Test
    void signup_이메일중복_409() {
        given(userRepository.existsByEmail("test@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup("test@test.com", "password123", "닉네임"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_성공() {
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getProvider()).willReturn(Provider.LOCAL);
        given(user.getPasswordHash()).willReturn("encoded");
        given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.login("test@test.com", "password123");

        assertThat(result.accessToken()).isEqualTo("at");
        verify(valueOps).set("RT:1:jti-uuid", "1", 1209600L, TimeUnit.SECONDS);
    }

    @Test
    void login_존재하지않는이메일_401() {
        given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("noone@test.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_소셜유저_409() {
        User user = mock(User.class);
        given(user.getProvider()).willReturn(Provider.KAKAO);
        given(userRepository.findByEmailAndDeletedAtIsNull("kakao@test.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("kakao@test.com", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_LOGIN_REQUIRED);
    }

    @Test
    void login_비밀번호불일치_401() {
        User user = mock(User.class);
        given(user.getProvider()).willReturn(Provider.LOCAL);
        given(user.getPasswordHash()).willReturn("encoded");
        given(userRepository.findByEmailAndDeletedAtIsNull("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login("test@test.com", "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}