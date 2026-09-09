package com.back.map.naver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class NaverStaticMapClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestHeadersUriSpec requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    private NaverStaticMapClient client;

    @BeforeEach
    void setUp() {
        client = new NaverStaticMapClient(restClient);
        given(restClient.get()).willReturn(requestSpec);
        given(requestSpec.uri(any(Function.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void returnsStaticMapImageBytes() {
        byte[] image = {1, 2, 3};
        given(responseSpec.body(byte[].class)).willReturn(image);

        assertThat(client.getMapImage(37.56, 126.82, 15)).isSameAs(image);
    }
}
