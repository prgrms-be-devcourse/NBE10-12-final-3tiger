package com.back.pushtoken.service;

import com.back.pushtoken.domain.PushToken;
import com.back.pushtoken.repository.PushTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PushTokenWriter {
    private final PushTokenRepository pushTokens;

    public PushTokenWriter(PushTokenRepository pushTokens) {
        this.pushTokens = pushTokens;
    }

    // 별도(REQUIRES_NEW) 트랜잭션으로 격리: saveAndFlush가 유니크 제약 위반으로 실패해도
    // 호출한 쪽의 바깥 트랜잭션까지 rollback-only로 오염되지 않도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySave(PushToken pushToken) {
        pushTokens.saveAndFlush(pushToken);
    }
}
