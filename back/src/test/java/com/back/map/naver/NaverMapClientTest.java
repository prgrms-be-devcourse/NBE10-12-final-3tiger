package com.back.map.naver;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.naver.dto.NaverReverseGeocodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class NaverMapClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestHeadersUriSpec requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    private NaverMapClient client;

    @BeforeEach
    void setUp() {
        client = new NaverMapClient(restClient);
        given(restClient.get()).willReturn(requestSpec);
        given(requestSpec.uri(any(Function.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void returnsSuccessfulResponse() {
        var response = new NaverReverseGeocodeResponse(
                new NaverReverseGeocodeResponse.Status(0, "ok", "done"),
                List.of()
        );
        given(responseSpec.body(NaverReverseGeocodeResponse.class)).willReturn(response);

        assertThat(client.reverseGeocode(37.5, 126.8)).isSameAs(response);
    }

    @Test
    void convertsRestClientFailureToBadGatewayError() {
        given(responseSpec.body(NaverReverseGeocodeResponse.class))
                .willThrow(new RestClientException("timeout"));

        assertNaverFailure();
    }

    @Test
    void convertsNaverErrorStatusToBadGatewayError() {
        given(responseSpec.body(NaverReverseGeocodeResponse.class)).willReturn(
                new NaverReverseGeocodeResponse(
                        new NaverReverseGeocodeResponse.Status(100, "invalid request", "failed"),
                        null
                )
        );

        assertNaverFailure();
    }

    private void assertNaverFailure() {
        assertThatThrownBy(() -> client.reverseGeocode(37.5, 126.8))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NAVER_REVERSE_GEOCODING_FAILED));
    }
}
