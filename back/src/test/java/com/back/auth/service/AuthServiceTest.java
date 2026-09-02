package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.auth.google.GoogleClient;
import com.back.auth.google.dto.GoogleTokenResponse;
import com.back.auth.google.dto.GoogleUserInfoResponse;
import com.back.auth.kakao.KakaoClient;
import com.back.auth.kakao.dto.KakaoTokenResponse;
import com.back.auth.kakao.dto.KakaoUserInfoResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.jwt.JwtProvider;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock Cursor<String> cursor;
    @Mock KakaoClient kakaoClient;
    @Mock GoogleClient googleClient;

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

    @Test
    void revokeAllRefreshTokens_해당사용자의모든토큰만삭제() {
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(true, true, true, false);
        given(cursor.next()).willReturn(
                "RT:1:first-jti",
                "RT:2:other-user-jti",
                "RT:1:second-jti"
        );

        authService.revokeAllRefreshTokens(1L);

        var optionsCaptor = org.mockito.ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getPattern()).isEqualTo("RT:1:*");
        verify(redisTemplate).delete(List.of("RT:1:first-jti", "RT:1:second-jti"));
        verify(cursor).close();
    }

    @Test
    void revokeAllRefreshTokens_토큰이없어도정상종료() {
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);
        given(cursor.hasNext()).willReturn(false);

        assertThatCode(() -> authService.revokeAllRefreshTokens(1L))
                .doesNotThrowAnyException();

        verify(redisTemplate, never()).delete(anyCollection());
        verify(cursor).close();
    }

    @Test
    void refresh_활성사용자는토큰재발급() {
        Claims claims = refreshClaims(1L, "old-jti");
        given(jwtProvider.parseRefreshToken("old-rt")).willReturn(claims);
        given(redisTemplate.delete("RT:1:old-jti")).willReturn(true);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(mock(User.class)));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("new-at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("new-rt");
        given(jwtProvider.getJti("new-rt")).willReturn("new-jti");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse response = authService.refresh("old-rt");

        assertThat(response).isEqualTo(new AuthResponse("new-at", "new-rt", false));
        verify(userRepository).findByIdAndDeletedAtIsNull(1L);
        verify(valueOps).set("RT:1:new-jti", "1", 1209600L, TimeUnit.SECONDS);
    }

    @Test
    void refresh_탈퇴사용자는토큰재발급실패() {
        Claims claims = refreshClaims(1L, "old-jti");
        given(jwtProvider.parseRefreshToken("old-rt")).willReturn(claims);
        given(redisTemplate.delete("RT:1:old-jti")).willReturn(true);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old-rt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(jwtProvider, never()).generateAccessToken(anyLong());
        verify(jwtProvider, never()).generateRefreshToken(anyLong());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void refresh_존재하지않는사용자는토큰재발급실패() {
        Claims claims = refreshClaims(1L, "old-jti");
        given(jwtProvider.parseRefreshToken("old-rt")).willReturn(claims);
        given(redisTemplate.delete("RT:1:old-jti")).willReturn(true);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old-rt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(jwtProvider, never()).generateAccessToken(anyLong());
        verify(jwtProvider, never()).generateRefreshToken(anyLong());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void oauthLogin_카카오_기존유저_로그인처리() {
        given(kakaoClient.exchangeToken("auth-code")).willReturn(new KakaoTokenResponse("kakao-at"));
        given(kakaoClient.getUserInfo("kakao-at")).willReturn(
                new KakaoUserInfoResponse(12345L,
                        new KakaoUserInfoResponse.KakaoAccount("user@kakao.com",
                                new KakaoUserInfoResponse.KakaoProfile("홍길동"))));
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.KAKAO, "12345"))
                .willReturn(Optional.of(user));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.oauthLogin("kakao", "auth-code");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void oauthLogin_카카오_신규유저_저장후토큰발급() {
        given(kakaoClient.exchangeToken("auth-code")).willReturn(new KakaoTokenResponse("kakao-at"));
        given(kakaoClient.getUserInfo("kakao-at")).willReturn(
                new KakaoUserInfoResponse(12345L,
                        new KakaoUserInfoResponse.KakaoAccount("user@kakao.com",
                                new KakaoUserInfoResponse.KakaoProfile("홍길동"))));
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.KAKAO, "12345"))
                .willReturn(Optional.empty());
        User saved = mock(User.class);
        given(saved.getId()).willReturn(2L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(2L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(2L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.oauthLogin("kakao", "auth-code");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.isNewUser()).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void oauthLogin_카카오_토큰교환실패_INVALID_AUTHORIZATION_CODE() {
        given(kakaoClient.exchangeToken(anyString()))
                .willThrow(new BusinessException(ErrorCode.INVALID_AUTHORIZATION_CODE));

        assertThatThrownBy(() -> authService.oauthLogin("kakao", "bad-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_AUTHORIZATION_CODE);
    }

    @Test
    void oauthLogin_지원안하는provider_INVALID_PROVIDER() {
        assertThatThrownBy(() -> authService.oauthLogin("apple", "some-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PROVIDER);
    }

    @Test
    void oauthLogin_구글_기존유저_로그인처리() {
        given(googleClient.exchangeToken("auth-code")).willReturn(new GoogleTokenResponse("google-at"));
        given(googleClient.getUserInfo("google-at")).willReturn(
                new GoogleUserInfoResponse("google-uid-123", "user@gmail.com", "홍길동"));
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.GOOGLE, "google-uid-123"))
                .willReturn(Optional.of(user));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.oauthLogin("google", "auth-code");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.isNewUser()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void oauthLogin_구글_신규유저_저장후토큰발급() {
        given(googleClient.exchangeToken("auth-code")).willReturn(new GoogleTokenResponse("google-at"));
        given(googleClient.getUserInfo("google-at")).willReturn(
                new GoogleUserInfoResponse("google-uid-123", "user@gmail.com", "홍길동"));
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.GOOGLE, "google-uid-123"))
                .willReturn(Optional.empty());
        User saved = mock(User.class);
        given(saved.getId()).willReturn(2L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(2L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(2L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        AuthResponse result = authService.oauthLogin("google", "auth-code");

        assertThat(result.accessToken()).isEqualTo("at");
        assertThat(result.isNewUser()).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void oauthLogin_구글_닉네임null_구글사용자폴백() {
        given(googleClient.exchangeToken("auth-code")).willReturn(new GoogleTokenResponse("google-at"));
        given(googleClient.getUserInfo("google-at")).willReturn(
                new GoogleUserInfoResponse("google-uid-123", "user@gmail.com", null));
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.GOOGLE, "google-uid-123"))
                .willReturn(Optional.empty());
        User saved = mock(User.class);
        given(saved.getId()).willReturn(1L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        authService.oauthLogin("google", "auth-code");

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("구글 사용자");
    }

    @Test
    void oauthLogin_카카오_이메일null_정상처리() {
        given(kakaoClient.exchangeToken("auth-code")).willReturn(new KakaoTokenResponse("kakao-at"));
        given(kakaoClient.getUserInfo("kakao-at")).willReturn(
                new KakaoUserInfoResponse(12345L,
                        new KakaoUserInfoResponse.KakaoAccount(null,
                                new KakaoUserInfoResponse.KakaoProfile("홍길동"))));
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.KAKAO, "12345"))
                .willReturn(Optional.of(user));
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        assertThatCode(() -> authService.oauthLogin("kakao", "auth-code")).doesNotThrowAnyException();
    }

    @Test
    void oauthLogin_카카오_닉네임null_카카오사용자폴백() {
        given(kakaoClient.exchangeToken("auth-code")).willReturn(new KakaoTokenResponse("kakao-at"));
        given(kakaoClient.getUserInfo("kakao-at")).willReturn(
                new KakaoUserInfoResponse(12345L,
                        new KakaoUserInfoResponse.KakaoAccount("user@kakao.com",
                                new KakaoUserInfoResponse.KakaoProfile(null))));
        given(userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.KAKAO, "12345"))
                .willReturn(Optional.empty());
        User saved = mock(User.class);
        given(saved.getId()).willReturn(1L);
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(jwtProvider.generateAccessToken(1L)).willReturn("at");
        given(jwtProvider.generateRefreshToken(1L)).willReturn("rt");
        given(jwtProvider.getJti("rt")).willReturn("jti-uuid");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(1209600L);

        authService.oauthLogin("kakao", "auth-code");

        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("카카오 사용자");
    }

    private Claims refreshClaims(Long userId, String jti) {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(String.valueOf(userId));
        given(claims.getId()).willReturn(jti);
        return claims;
    }
}
