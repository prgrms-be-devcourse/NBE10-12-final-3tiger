package com.back.user.email;

public interface EmailSender {

    void sendVerificationCode(String email, String code);
}
