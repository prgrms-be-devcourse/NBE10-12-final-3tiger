package com.back.auth.kakao;

import com.back.auth.kakao.dto.KakaoTokenResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings({"rawtypes", "unchecked"})
class KakaoClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final RestClient.RequestBodyUriSpec requestSpec =
            mock(RestClient.RequestBodyUriSpec.class);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    private KakaoClient client;

    @BeforeEach
    void setUp() {
        KakaoProperties properties = new KakaoProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("app://oauth/kakao");
        client = new KakaoClient(properties, restClient);

        given(restClient.post()).willReturn(requestSpec);
        given(requestSpec.uri(anyString())).willReturn(requestSpec);
        given(requestSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestSpec);
        given(requestSpec.body(any(MultiValueMap.class))).willReturn(requestSpec);
        given(requestSpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void treatsKakao4xxAsInvalidAuthorizationCode() {
        given(responseSpec.body(KakaoTokenResponse.class)).willThrow(
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null
                )
        );

        assertThatThrownBy(() -> client.exchangeToken("invalid-code"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_AUTHORIZATION_CODE));
    }

    @Test
    void treatsKakao5xxAsExternalServerFailure() {
        given(responseSpec.body(KakaoTokenResponse.class)).willThrow(
                HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        null,
                        null
                )
        );

        assertThatThrownBy(() -> client.exchangeToken("valid-code"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SOCIAL_SERVER_ERROR));
    }
}
