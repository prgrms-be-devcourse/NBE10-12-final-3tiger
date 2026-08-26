package com.back.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON_500",
            "서버 내부 오류가 발생했습니다."
    ),

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_404",
            "사용자를 찾을 수 없습니다."
    ),

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTH_409_1",
            "이미 사용 중인 이메일입니다."
    ),

    SOCIAL_LOGIN_REQUIRED(
            HttpStatus.CONFLICT,
            "AUTH_409_2",
            "소셜 로그인으로 가입된 계정입니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "AUTH_401_1",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
