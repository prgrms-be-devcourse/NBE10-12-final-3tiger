package com.back.auth.google.dto;

public record GoogleUserInfoResponse(
        String id,
        String email,
        String name
) {}