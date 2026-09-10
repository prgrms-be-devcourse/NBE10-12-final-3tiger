package com.back.pushtoken.service;

import com.back.pushtoken.client.ExpoPushClient;
import com.back.pushtoken.client.ExpoPushSendResult;
import com.back.pushtoken.domain.PushToken;
import com.back.pushtoken.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PushSendService {

    private static final Logger log = LoggerFactory.getLogger(PushSendService.class);

    private final PushTokenRepository pushTokens;
    private final PushTokenService pushTokenService;
    private final ExpoPushClient expoPushClient;

    public PushSendService(PushTokenRepository pushTokens, PushTokenService pushTokenService,
                           ExpoPushClient expoPushClient) {
        this.pushTokens = pushTokens;
        this.pushTokenService = pushTokenService;
        this.expoPushClient = expoPushClient;
    }

    @Async("pushSendExecutor")
    public void sendToUser(Long userId, String title, String body) {
        List<PushToken> tokens = pushTokens.findByUser_IdOrderByUpdatedAtDesc(userId);
        if (tokens.isEmpty()) {
            return;
        }
        for (PushToken pushToken : tokens) {
            dispatch(userId, pushToken.getToken(), title, body);
        }
    }

    private void dispatch(Long userId, String token, String title, String body) {
        try {
            ExpoPushSendResult result = expoPushClient.send(token, title, body);
            if (result.tokenExpired()) {
                pushTokenService.unregister(token);
                log.info("[push] 만료된 토큰을 삭제했습니다. userId={}", userId);
            } else if (!result.succeeded()) {
                log.warn("[push] Expo 발송 실패 userId={}, message={}", userId, result.message());
            }
        } catch (RuntimeException e) {
            // 발송 실패가 알림 생성 흐름을 막지 않도록 삼킨다.
            log.warn("[push] Expo 발송 중 예외 userId={}", userId, e);
        }
    }
}
