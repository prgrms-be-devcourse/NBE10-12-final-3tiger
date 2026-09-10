package com.back.pushtoken.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.pushtoken.service.PushTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
public class PushTokenController {

    private final PushTokenService service;

    public PushTokenController(PushTokenService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<Void> register(@CurrentUserId Long userId, @Valid @RequestBody RegisterRequest request) {
        service.register(userId, request.token(), request.platform());
        return ApiResponse.ok("푸시 토큰을 등록했습니다.", null);
    }

    @DeleteMapping
    ApiResponse<Void> unregister(@Valid @RequestBody UnregisterRequest request) {
        service.unregister(request.token());
        return ApiResponse.ok("푸시 토큰을 해제했습니다.", null);
    }

    record RegisterRequest(
            @NotBlank(message = "푸시 토큰은 필수입니다.") String token,
            @NotBlank(message = "플랫폼은 필수입니다.") String platform) {}

    record UnregisterRequest(@NotBlank(message = "푸시 토큰은 필수입니다.") String token) {}
}
