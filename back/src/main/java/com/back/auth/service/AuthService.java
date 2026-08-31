package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
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
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final KakaoClient kakaoClient;


    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getProvider() != Provider.LOCAL) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_REQUIRED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issueTokens(user.getId());
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();
        String redisKey = "RT:" + userId + ":" + jti;

        if (!Boolean.TRUE.equals(redisTemplate.delete(redisKey))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(userId);
    }

    public void logout(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            return;
        }

        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();
        redisTemplate.delete("RT:" + userId + ":" + jti);
    }

    @Transactional
    public AuthResponse kakaoLogin(String code) {
        KakaoTokenResponse tokenResponse = kakaoClient.exchangeToken(code);
        KakaoUserInfoResponse userInfo = kakaoClient.getUserInfo(tokenResponse.accessToken());

        String providerUid = String.valueOf(userInfo.id());
        KakaoUserInfoResponse.KakaoAccount account = userInfo.kakaoAccount();
        String email = account != null ? account.email() : null;
        String nickname = (account != null && account.profile() != null && account.profile().nickname() != null)
                ? account.profile().nickname()
                : "카카오 사용자";

        User user = userRepository.findByProviderAndProviderUidAndDeletedAtIsNull(Provider.KAKAO, providerUid)
                .orElseGet(() -> userRepository.save(User.createKakao(providerUid, email, nickname)));

        return issueTokens(user.getId());
    }

    public void revokeAllRefreshTokens(Long userId) {
        String keyPrefix = "RT:" + userId + ":";
        ScanOptions options = ScanOptions.scanOptions()
                .match(keyPrefix + "*")
                .count(100)
                .build();
        List<String> refreshTokenKeys = new ArrayList<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (key.startsWith(keyPrefix)) {
                    refreshTokenKeys.add(key);
                }
            }
        }

        if (!refreshTokenKeys.isEmpty()) {
            redisTemplate.delete(refreshTokenKeys);
        }
    }

    private AuthResponse issueTokens(Long userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        String jti = jwtProvider.getJti(refreshToken);

        redisTemplate.opsForValue().set(
                "RT:" + userId + ":" + jti,
                "1",
                jwtProvider.getRefreshTokenExpiry(),
                TimeUnit.SECONDS
        );
        return new AuthResponse(accessToken, refreshToken);
    }
}
