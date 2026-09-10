package com.back.pushtoken.client;

import com.back.pushtoken.client.dto.ExpoPushMessage;
import com.back.pushtoken.client.dto.ExpoPushSendResponse;
import com.back.pushtoken.config.ExpoPushProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExpoPushClient {

    private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private final RestClient restClient;
    private final ExpoPushProperties properties;

    public ExpoPushClient(@Qualifier("expoPushRestClient") RestClient restClient, ExpoPushProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 통신/파싱 실패도 예외로 던지지 않고 {@link ExpoPushSendResult#failure} 로 반환한다. */
    public ExpoPushSendResult send(String token, String title, String body) {
        try {
            ExpoPushSendResponse response = restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExpoPushMessage(token, title, body))
                    .retrieve()
                    .body(ExpoPushSendResponse.class);

            return interpret(response);
        } catch (RestClientException e) {
            return ExpoPushSendResult.failure(e.getMessage());
        }
    }

    private ExpoPushSendResult interpret(ExpoPushSendResponse response) {
        if (response == null || response.data() == null) {
            return ExpoPushSendResult.failure("Expo 응답이 비어 있습니다.");
        }

        ExpoPushSendResponse.Ticket ticket = response.data();
        if ("ok".equals(ticket.status())) {
            return ExpoPushSendResult.ok();
        }

        boolean deviceNotRegistered = ticket.details() != null
                && DEVICE_NOT_REGISTERED.equals(ticket.details().error());
        return deviceNotRegistered
                ? ExpoPushSendResult.deviceNotRegistered()
                : ExpoPushSendResult.failure(ticket.message());
    }
}
