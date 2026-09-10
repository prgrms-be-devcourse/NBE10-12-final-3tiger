package com.back.pushtoken.service;

import com.back.pushtoken.domain.PushToken;
import com.back.pushtoken.repository.PushTokenRepository;
import com.back.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PushTokenService {

    private final PushTokenRepository pushTokens;
    private final PushTokenWriter pushTokenWriter;
    private final UserRepository users;

    public PushTokenService(PushTokenRepository pushTokens, PushTokenWriter pushTokenWriter, UserRepository users) {
        this.pushTokens = pushTokens;
        this.pushTokenWriter = pushTokenWriter;
        this.users = users;
    }

    /**
     * 기기 토큰을 현재 인증된 사용자 앞으로 등록한다. 프론트가 매 마운트 시 호출하므로 멱등해야 한다.
     * <p>
     * upsert 기준은 token 컬럼이다. 동일 token 이 이미 있으면 소유자를 현재 사용자로 재지정하고
     * (연관관계 변경 → {@code updatedAt} 갱신), 없으면 새로 저장한다.
     */
    @Transactional
    public void register(Long userId, String token, String platform) {
        pushTokens.findByToken(token).ifPresentOrElse(
                existing -> existing.reassignTo(users.getReferenceById(userId), platform),
                () -> insertOrReassign(userId, token, platform));
    }

    private void insertOrReassign(Long userId, String token, String platform) {
        try {
            pushTokenWriter.trySave(new PushToken(users.getReferenceById(userId), token, platform));
        } catch (DataIntegrityViolationException e) {
            // uk_push_token_token 위반 = 동시에 같은 토큰이 등록됨 → 방금 저장된 레코드를 현재 사용자로 재지정
            pushTokens.findByToken(token)
                    .ifPresent(existing -> existing.reassignTo(users.getReferenceById(userId), platform));
        }
    }

    /**
     * 기기 토큰을 삭제한다(로그아웃 시 호출). 인증 사용자와 무관하게 token 값으로 지운다.
     * 레코드가 없어도 에러 없이 넘어간다(멱등).
     */
    @Transactional
    public void unregister(String token) {
        pushTokens.deleteByToken(token);
    }
}
