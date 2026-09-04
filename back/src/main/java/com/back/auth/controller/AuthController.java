package com.back.auth.controller;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
import com.back.auth.dto.LogoutRequest;
import com.back.auth.dto.OAuthLoginRequest;
import com.back.auth.dto.RefreshRequest;
import com.back.auth.service.AuthService;
import com.back.global.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.ok(ApiResponse.ok("로그인이 완료되었습니다.", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("토큰이 재발급되었습니다.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok("로그아웃이 완료되었습니다.", null));
    }

    @PostMapping("/oauth/{provider}/login")
    public ResponseEntity<ApiResponse<AuthResponse>> oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request
    ) {
        AuthResponse response = authService.oauthLogin(provider, request.authorizationCode());
        return ResponseEntity.ok(ApiResponse.ok("소셜 로그인이 완료되었습니다.", response));
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(@RequestParam String code) {
        try {
            AuthResponse res = authService.oauthLogin("kakao", code);
            String location = "front://oauth-callback"
                    + "?accessToken=" + URLEncoder.encode(res.accessToken(), StandardCharsets.UTF_8)
                    + "&refreshToken=" + URLEncoder.encode(res.refreshToken(), StandardCharsets.UTF_8)
                    + "&isNewUser=" + res.isNewUser();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location))
                    .build();
        } catch (Exception e) {
            String location = "front://oauth-callback?error="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(location))
                    .build();
        }
    }
}