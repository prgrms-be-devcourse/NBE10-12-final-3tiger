package com.back.auth.controller;

import com.back.auth.dto.AuthResponse;
import com.back.auth.dto.LoginRequest;
import com.back.auth.dto.LogoutRequest;
import com.back.auth.dto.RefreshRequest;
import com.back.auth.service.AuthService;
import com.back.global.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}