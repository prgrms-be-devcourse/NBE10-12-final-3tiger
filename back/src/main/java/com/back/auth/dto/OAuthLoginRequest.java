package com.back.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(@NotBlank String authorizationCode) {}