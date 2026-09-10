package com.back.pushtoken.client;

import com.back.pushtoken.client.dto.ExpoPushSendResponse;
import com.back.pushtoken.config.ExpoPushProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class ExpoPushClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestBodyUriSpec bodySpec = mock(RestClient.RequestBodyUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    private ExpoPushClient client;

    @BeforeEach
    void setUp() {
        client = new ExpoPushClient(restClient, new ExpoPushProperties());
        given(restClient.post()).willReturn(bodySpec);
        given(bodySpec.uri(anyString())).willReturn(bodySpec);
        given(bodySpec.contentType(any(MediaType.class))).willReturn(bodySpec);
        given(bodySpec.body(any(Object.class))).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);
    }

    private void stubResponse(ExpoPushSendResponse response) {
        given(responseSpec.body(ExpoPushSendResponse.class)).willReturn(response);
    }

    @Test
    @DisplayName("t1: status=ok 응답이면 succeeded=true")
    void t1() {
        stubResponse(new ExpoPushSendResponse(new ExpoPushSendResponse.Ticket("ok", "receipt-1", null, null)));

        ExpoPushSendResult result = client.send("ExponentPushToken[a]", "제목", "내용");

        assertThat(result.succeeded()).isTrue();
        assertThat(result.tokenExpired()).isFalse();
    }

    @Test
    @DisplayName("t2: details.error=DeviceNotRegistered 면 tokenExpired=true")
    void t2() {
        stubResponse(new ExpoPushSendResponse(new ExpoPushSendResponse.Ticket(
                "error", null, "...", new ExpoPushSendResponse.Details("DeviceNotRegistered"))));

        ExpoPushSendResult result = client.send("ExponentPushToken[a]", "제목", "내용");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.tokenExpired()).isTrue();
    }

    @Test
    @DisplayName("t3: 그 외 error 는 실패로 처리하되 tokenExpired=false")
    void t3() {
        stubResponse(new ExpoPushSendResponse(new ExpoPushSendResponse.Ticket(
                "error", null, "Message too big", new ExpoPushSendResponse.Details("MessageTooBig"))));

        ExpoPushSendResult result = client.send("ExponentPushToken[a]", "제목", "내용");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.tokenExpired()).isFalse();
        assertThat(result.message()).isEqualTo("Message too big");
    }

    @Test
    @DisplayName("t4: 응답 바디가 비어 있으면 실패로 처리")
    void t4() {
        stubResponse(null);

        ExpoPushSendResult result = client.send("ExponentPushToken[a]", "제목", "내용");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.tokenExpired()).isFalse();
    }

    @Test
    @DisplayName("t5: 통신 예외는 삼켜서 실패 결과로 반환 (예외 전파 안 함)")
    void t5() {
        given(responseSpec.body(ExpoPushSendResponse.class)).willThrow(new RestClientException("timeout"));

        ExpoPushSendResult result = client.send("ExponentPushToken[a]", "제목", "내용");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.tokenExpired()).isFalse();
    }
}
