package com.back.place.naver;

public class NaverTypoCorrectionException extends RuntimeException {

    public NaverTypoCorrectionException(Throwable cause) {
        super(cause);
    }

    public NaverTypoCorrectionException(String message) {
        super(message);
    }
}
