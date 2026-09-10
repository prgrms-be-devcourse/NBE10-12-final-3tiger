package com.back.pushtoken.service;

import com.back.pushtoken.domain.PushToken;
import com.back.pushtoken.repository.PushTokenRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    private static final String TOKEN = "ExponentPushToken[abc123]";
    private static final String PLATFORM = "android";

    @Mock private PushTokenRepository pushTokens;
    @Mock private PushTokenWriter pushTokenWriter;
    @Mock private UserRepository users;

    @InjectMocks private PushTokenService service;

    private User user(long id) {
        User user = User.createLocal("owner@test.com", "dummy-hash", "주인");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("t1: 신규 토큰이면 새 레코드를 저장한다")
    void t1() {
        // given
        given(pushTokens.findByToken(TOKEN)).willReturn(Optional.empty());
        given(users.getReferenceById(1L)).willReturn(user(1L));

        // when
        service.register(1L, TOKEN, PLATFORM);

        // then
        verify(pushTokenWriter).trySave(any(PushToken.class));
    }

    @Test
    @DisplayName("t2: 이미 존재하는 토큰이면 저장 없이 소유자를 현재 사용자로 재지정한다")
    void t2() {
        // given
        PushToken existing = new PushToken(user(1L), TOKEN, PLATFORM);
        given(pushTokens.findByToken(TOKEN)).willReturn(Optional.of(existing));
        given(users.getReferenceById(2L)).willReturn(user(2L));

        // when
        service.register(2L, TOKEN, PLATFORM);

        // then
        assertThat(existing.getUser().getId()).isEqualTo(2L);
        verify(pushTokenWriter, never()).trySave(any());
    }

    @Test
    @DisplayName("t3: 저장 중 유니크 제약 위반이 나면(동시 등록) 기존 레코드를 재지정해 멱등 처리한다")
    void t3() {
        // given
        PushToken concurrentlyInserted = new PushToken(user(9L), TOKEN, PLATFORM);
        given(pushTokens.findByToken(TOKEN))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(concurrentlyInserted));
        given(users.getReferenceById(2L)).willReturn(user(2L));
        willThrow(new DataIntegrityViolationException("uk_push_token_token"))
                .given(pushTokenWriter).trySave(any(PushToken.class));

        // when
        service.register(2L, TOKEN, PLATFORM);

        // then
        assertThat(concurrentlyInserted.getUser().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("t4: 해제는 token 값으로 삭제하며, 레코드가 없어도 예외가 없다(멱등)")
    void t4() {
        // given
        given(pushTokens.deleteByToken(TOKEN)).willReturn(0L);

        // when
        service.unregister(TOKEN);

        // then
        verify(pushTokens).deleteByToken(TOKEN);
    }
}
