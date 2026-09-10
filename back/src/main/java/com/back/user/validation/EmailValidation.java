package com.back.user.validation;

public final class EmailValidation {

    public static final String REGEX = "^[^@\\s]+@(?:[^@\\s.]+\\.)+[^@\\s.]+$";
    public static final int MAX_LENGTH = 255;

    private EmailValidation() {
    }
}
