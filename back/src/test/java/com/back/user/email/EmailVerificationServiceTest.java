package com.back.user.email;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.email.dto.EmailVerificationResponse;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE_KEY = "EMAIL_VERIFY:CODE:" + EMAIL;
    private static final String ATTEMPTS_KEY = "EMAIL_VERIFY:ATTEMPTS:" + EMAIL;
    private static final String COOLDOWN_KEY = "EMAIL_VERIFY:COOLDOWN:" + EMAIL;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userRepository, redisTemplate, emailSender);
    }

    @Test
    void sendStoresSixDigitCodeWithTtlAndSendsMail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).willReturn(true);

        service.sendVerificationCode(EMAIL);

        var codeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(CODE_KEY), codeCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        verify(valueOperations).set(ATTEMPTS_KEY, "0", Duration.ofMinutes(5));
        verify(emailSender).sendVerificationCode(EMAIL, codeCaptor.getValue());
    }

    @Test
    void sendRejectsExistingEmail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        verifyNoInteractions(redisTemplate, emailSender);
    }

    @Test
    void sendRejectsDuringCooldown() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).willReturn(false);

        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_RESEND_LIMIT));

        verify(emailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendCleansRedisStateWhenMailSendingFails() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(60))).willReturn(true);
        willThrow(new IllegalStateException("smtp failure"))
                .given(emailSender).sendVerificationCode(org.mockito.ArgumentMatchers.eq(EMAIL), anyString());

        assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_SEND_FAILED));

        verify(redisTemplate).delete(java.util.List.of(CODE_KEY, ATTEMPTS_KEY, COOLDOWN_KEY));
    }

    @Test
    void verifyIssuesTicketAndStoresItForThirtyMinutes() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn("0");
        given(valueOperations.get(CODE_KEY)).willReturn("123456");
        given(redisTemplate.execute(
                any(RedisScript.class),
                eq(java.util.List.of(CODE_KEY, ATTEMPTS_KEY)),
                eq("123456")
        ))
                .willReturn(1L);

        EmailVerificationResponse response = service.verifyCode(EMAIL, "123456");

        assertThat(response.verificationToken()).isNotBlank();
        verify(valueOperations).set(
                "EMAIL_VERIFY:TICKET:" + response.verificationToken(),
                EMAIL,
                Duration.ofMinutes(30)
        );
    }

    @Test
    void verifyDoesNotDeleteResentCodeWhenCodeChangesBeforeAtomicConsume() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn("0");
        given(valueOperations.get(CODE_KEY)).willReturn("123456");
        given(redisTemplate.execute(
                any(RedisScript.class),
                eq(java.util.List.of(CODE_KEY, ATTEMPTS_KEY)),
                eq("123456")
        ))
                .willReturn(0L);

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID));

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(java.util.List.of(CODE_KEY, ATTEMPTS_KEY)),
                eq("123456")
        );
        verify(valueOperations, never()).getAndDelete(CODE_KEY);
        verify(redisTemplate, never()).delete(CODE_KEY);
    }

    @Test
    void verifyRejectsWrongCodeAndIncrementsAttempts() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn("0");
        given(valueOperations.get(CODE_KEY)).willReturn("123456");
        given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(1L);

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "654321"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID));

        verify(valueOperations).increment(ATTEMPTS_KEY);
    }

    @Test
    void verifyBlocksAfterFifthFailure() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn("4");
        given(valueOperations.get(CODE_KEY)).willReturn("123456");
        given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(5L);

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "654321"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED));

        verify(redisTemplate).delete(CODE_KEY);
    }

    @Test
    void verifyRejectsExpiredCode() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn(null);
        given(valueOperations.get(CODE_KEY)).willReturn(null);

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID));
    }

    @Test
    void verifiedCodeCannotBeReused() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ATTEMPTS_KEY)).willReturn("0", (String) null);
        given(valueOperations.get(CODE_KEY)).willReturn("123456", (String) null);
        given(redisTemplate.execute(
                any(RedisScript.class),
                eq(java.util.List.of(CODE_KEY, ATTEMPTS_KEY)),
                eq("123456")
        ))
                .willReturn(1L);

        service.verifyCode(EMAIL, "123456");

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID));
    }

    @Test
    void consumeTicketIsAtomicAndOneTimeOnly() {
        String token = "verification-token";
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete("EMAIL_VERIFY:TICKET:" + token))
                .willReturn(EMAIL, (String) null);

        assertThatCode(() -> service.consumeVerificationTicket(EMAIL, token)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.consumeVerificationTicket(EMAIL, token))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
    }

    @Test
    void consumeTicketRejectsDifferentEmailAndConsumesToken() {
        String token = "verification-token";
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete("EMAIL_VERIFY:TICKET:" + token)).willReturn("other@example.com");

        assertThatThrownBy(() -> service.consumeVerificationTicket(EMAIL, token))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));

        verify(valueOperations).getAndDelete("EMAIL_VERIFY:TICKET:" + token);
    }

    @Test
    void consumeTicketRejectsMissingTokenWithoutAccessingRedis() {
        assertThatThrownBy(() -> service.consumeVerificationTicket(EMAIL, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));

        verifyNoInteractions(redisTemplate);
    }
}
