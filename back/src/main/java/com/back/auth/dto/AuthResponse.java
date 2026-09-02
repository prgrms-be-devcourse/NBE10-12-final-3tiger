package com.back.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, boolean isNewUser) {}