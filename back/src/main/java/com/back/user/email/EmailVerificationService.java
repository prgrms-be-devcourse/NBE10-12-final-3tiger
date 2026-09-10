package com.back.user.email;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.email.dto.EmailVerificationResponse;
import com.back.user.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
public class EmailVerificationService {

    private static final String CODE_PREFIX = "EMAIL_VERIFY:CODE:";
    private static final String ATTEMPTS_PREFIX = "EMAIL_VERIFY:ATTEMPTS:";
    private static final String COOLDOWN_PREFIX = "EMAIL_VERIFY:COOLDOWN:";
    private static final String TICKET_PREFIX = "EMAIL_VERIFY:TICKET:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration TICKET_TTL = Duration.ofMinutes(30);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final DefaultRedisScript<Long> CONSUME_CODE_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('GET', KEYS[1])
                    if current == ARGV[1] then
                        redis.call('DEL', KEYS[1])
                        redis.call('DEL', KEYS[2])
                        return 1
                    end
                    return 0
                    """, Long.class);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            EmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.emailSender = emailSender;
    }

    public void sendVerificationCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        ValueOperations<String, String> values = redisTemplate.opsForValue();
        String cooldownKey = cooldownKey(email);
        Boolean acquired = values.setIfAbsent(cooldownKey, "1", COOLDOWN_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_RESEND_LIMIT);
        }

        String codeKey = codeKey(email);
        String attemptsKey = attemptsKey(email);
        String code = generateCode();
        values.set(codeKey, code, CODE_TTL);
        values.set(attemptsKey, "0", CODE_TTL);

        try {
            emailSender.sendVerificationCode(email, code);
        } catch (RuntimeException exception) {
            redisTemplate.delete(List.of(codeKey, attemptsKey, cooldownKey));
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public EmailVerificationResponse verifyCode(String email, String code) {
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        String attemptsKey = attemptsKey(email);
        if (readAttempts(values.get(attemptsKey)) >= MAX_FAILED_ATTEMPTS) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        String codeKey = codeKey(email);
        String storedCode = values.get(codeKey);
        if (storedCode == null) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }
        if (!storedCode.equals(code)) {
            Long attempts = values.increment(attemptsKey);
            if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        Long consumed = redisTemplate.execute(
                CONSUME_CODE_IF_MATCHES_SCRIPT,
                List.of(codeKey, attemptsKey),
                code
        );
        if (!Long.valueOf(1L).equals(consumed)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        String token = generateToken();
        values.set(ticketKey(token), email, TICKET_TTL);
        return new EmailVerificationResponse(token);
    }

    public void consumeVerificationTicket(String email, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        String verifiedEmail = redisTemplate.opsForValue().getAndDelete(ticketKey(token));
        if (!email.equals(verifiedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
    }

    private String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private int readAttempts(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return MAX_FAILED_ATTEMPTS;
        }
    }

    private String codeKey(String email) {
        return CODE_PREFIX + email;
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email;
    }

    private String ticketKey(String token) {
        return TICKET_PREFIX + token;
    }
}
