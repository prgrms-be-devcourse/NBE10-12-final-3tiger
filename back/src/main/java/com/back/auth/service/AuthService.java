package com.back.auth.service;

import com.back.auth.dto.AuthResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.jwt.JwtProvider;
import com.back.user.domain.Provider;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;


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

        if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        redisTemplate.delete(redisKey);
        return issueTokens(userId);
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