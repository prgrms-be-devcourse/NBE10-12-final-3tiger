package com.back.user.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private static final String SUBJECT = "오늘의산책 이메일 인증번호";

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email-verification.from}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("오늘의산책 이메일 인증번호는 " + code
                + "입니다. 인증번호는 5분간 유효합니다.");
        mailSender.send(message);
    }
}
