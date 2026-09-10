package com.back.pushtoken.service;

import com.back.pushtoken.client.ExpoPushClient;
import com.back.pushtoken.client.ExpoPushSendResult;
import com.back.pushtoken.domain.PushToken;
import com.back.pushtoken.repository.PushTokenRepository;
import com.back.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushSendServiceTest {

    private static final long USER_ID = 1L;

    @Mock private PushTokenRepository pushTokens;
    @Mock private PushTokenService pushTokenService;
    @Mock private ExpoPushClient expoPushClient;

    @InjectMocks private PushSendService service;

    private PushToken pushToken(String value) {
        return new PushToken(User.createLocal("owner@test.com", "hash", "주인"), value, "android");
    }

    @Test
    @DisplayName("t1: 등록된 토큰이 없으면 발송을 시도하지 않는다")
    void t1() {
        given(pushTokens.findByUser_IdOrderByUpdatedAtDesc(USER_ID)).willReturn(List.of());

        service.sendToUser(USER_ID, "제목", "내용");

        verify(expoPushClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("t2: 발송 성공 시 토큰을 삭제하지 않는다")
    void t2() {
        given(pushTokens.findByUser_IdOrderByUpdatedAtDesc(USER_ID)).willReturn(List.of(pushToken("tok-a")));
        given(expoPushClient.send("tok-a", "제목", "내용")).willReturn(ExpoPushSendResult.ok());

        service.sendToUser(USER_ID, "제목", "내용");

        verify(pushTokenService, never()).unregister(any());
    }

    @Test
    @DisplayName("t3: DeviceNotRegistered 결과면 해당 토큰을 등록 해제한다")
    void t3() {
        given(pushTokens.findByUser_IdOrderByUpdatedAtDesc(USER_ID)).willReturn(List.of(pushToken("tok-a")));
        given(expoPushClient.send("tok-a", "제목", "내용")).willReturn(ExpoPushSendResult.deviceNotRegistered());

        service.sendToUser(USER_ID, "제목", "내용");

        verify(pushTokenService).unregister("tok-a");
    }

    @Test
    @DisplayName("t4: 일반 발송 실패는 토큰을 삭제하지 않고 넘어간다")
    void t4() {
        given(pushTokens.findByUser_IdOrderByUpdatedAtDesc(USER_ID)).willReturn(List.of(pushToken("tok-a")));
        given(expoPushClient.send("tok-a", "제목", "내용")).willReturn(ExpoPushSendResult.failure("MessageRateExceeded"));

        service.sendToUser(USER_ID, "제목", "내용");

        verify(pushTokenService, never()).unregister(any());
    }

    @Test
    @DisplayName("t5: 발송 중 예외가 나도 전파하지 않고 다음 토큰을 계속 처리한다")
    void t5() {
        given(pushTokens.findByUser_IdOrderByUpdatedAtDesc(USER_ID))
                .willReturn(List.of(pushToken("tok-a"), pushToken("tok-b")));
        given(expoPushClient.send("tok-a", "제목", "내용")).willThrow(new RuntimeException("boom"));
        given(expoPushClient.send("tok-b", "제목", "내용")).willReturn(ExpoPushSendResult.ok());

        assertThatCode(() -> service.sendToUser(USER_ID, "제목", "내용")).doesNotThrowAnyException();

        verify(expoPushClient).send("tok-b", "제목", "내용");
    }
}
