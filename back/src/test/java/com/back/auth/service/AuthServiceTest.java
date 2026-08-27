package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.jwt.JwtProvider;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
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

import io.jsonwebtoken.JwtException;

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


    @Test
    void login_성공() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
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

    @Test
    void logout_유효한토큰_Redis키삭제() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        given(jwtProvider.parseRefreshToken("valid-rt")).willReturn(claims);
        given(claims.getSubject()).willReturn("1");
        given(claims.getId()).willReturn("jti-uuid");

        authService.logout("valid-rt");

        verify(redisTemplate).delete("RT:1:jti-uuid");
    }

    @Test
    void logout_만료된토큰_무시하고정상처리() {
        given(jwtProvider.parseRefreshToken("expired-rt")).willThrow(new JwtException("expired"));

        authService.logout("expired-rt");

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void logout_유효하지않은토큰_무시하고정상처리() {
        given(jwtProvider.parseRefreshToken("bad-rt")).willThrow(new IllegalArgumentException("invalid"));

        authService.logout("bad-rt");

        verify(redisTemplate, never()).delete(anyString());
    }
}