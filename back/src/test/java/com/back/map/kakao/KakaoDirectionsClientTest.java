package com.back.map.kakao;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.kakao.dto.KakaoRouteDirectionsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class KakaoDirectionsClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestHeadersUriSpec requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    private KakaoDirectionsClient client;

    @BeforeEach
    void setUp() {
        client = new KakaoDirectionsClient(restClient);
        given(restClient.get()).willReturn(requestSpec);
        given(requestSpec.uri(any(Function.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void convertsQuotaResponseToServiceUnavailableError() {
        given(responseSpec.body(KakaoRouteDirectionsResponse.class))
                .willThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        assertError(ErrorCode.KAKAO_DIRECTIONS_QUOTA_EXCEEDED);
    }

    @Test
    void convertsRestClientFailureToBadGatewayError() {
        given(responseSpec.body(KakaoRouteDirectionsResponse.class))
                .willThrow(new RestClientException("timeout"));

        assertError(ErrorCode.KAKAO_DIRECTIONS_FAILED);
    }

    @Test
    void convertsNullBodyToBadGatewayError() {
        given(responseSpec.body(KakaoRouteDirectionsResponse.class)).willReturn(null);

        assertError(ErrorCode.KAKAO_DIRECTIONS_FAILED);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> client.getWalk(
                37.50, 126.80, 37.56, 126.83, "코스 출발점"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
