package com.back.place.naver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class NaverTypoCorrectionClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestHeadersUriSpec requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    private NaverTypoCorrectionClient client;

    @BeforeEach
    void setUp() {
        client = new NaverTypoCorrectionClient(restClient, new ObjectMapper());
        given(restClient.get()).willReturn(requestSpec);
        given(requestSpec.uri(any(Function.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void returnsCorrectedQuery() {
        given(responseSpec.body(String.class))
                .willReturn("{\"errata\":\"네이버\"}");

        assertThat(client.correct("spdlqj")).isEqualTo("네이버");
    }

    @Test
    void throwsDomainExceptionWhenResponseIsNotJson() {
        given(responseSpec.body(String.class)).willReturn("not-json");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.correct("spdlqj"))
                .isInstanceOf(NaverTypoCorrectionException.class);
    }
}
