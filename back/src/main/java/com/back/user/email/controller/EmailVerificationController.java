package com.back.user.email.controller;

import com.back.global.api.ApiResponse;
import com.back.user.email.EmailVerificationService;
import com.back.user.email.dto.EmailVerificationConfirmRequest;
import com.back.user.email.dto.EmailVerificationResponse;
import com.back.user.email.dto.EmailVerificationSendRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/email-verifications")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody EmailVerificationSendRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ApiResponse.ok("인증번호가 발송되었습니다.", null);
    }

    @PostMapping("/verify")
    public ApiResponse<EmailVerificationResponse> verify(
            @Valid @RequestBody EmailVerificationConfirmRequest request
    ) {
        return ApiResponse.ok(
                "이메일 인증이 완료되었습니다.",
                emailVerificationService.verifyCode(request.email(), request.code())
        );
    }
}
