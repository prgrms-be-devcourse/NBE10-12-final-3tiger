package com.back.global.api;

public record ApiResponse<T>(String resultCode, String message, T data) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("200-1", message, data);
    }
}
