package com.back.place.kakao;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.place.kakao.dto.KakaoPlaceSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class KakaoPlaceClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestHeadersUriSpec requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    private KakaoPlaceClient client;

    @BeforeEach
    void setUp() {
        client = new KakaoPlaceClient(restClient);
        given(restClient.get()).willReturn(requestSpec);
        given(requestSpec.uri(any(Function.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void convertsRestClientExceptionToPlaceSearchFailure() {
        given(responseSpec.body(KakaoPlaceSearchResponse.class))
                .willThrow(new RestClientException("Kakao API failure"));

        assertThatThrownBy(() -> client.search("서울식물원", 15))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.KAKAO_PLACE_SEARCH_FAILED));
    }

    @Test
    void convertsNullResponseBodyToPlaceSearchFailure() {
        given(responseSpec.body(KakaoPlaceSearchResponse.class)).willReturn(null);

        assertThatThrownBy(() -> client.search("서울식물원", 15))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.KAKAO_PLACE_SEARCH_FAILED));
    }
}
