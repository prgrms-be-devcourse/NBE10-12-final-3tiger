package com.back.auth.kakao;

import com.back.auth.kakao.dto.KakaoTokenResponse;
import com.back.auth.kakao.dto.KakaoUserInfoResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoClient {

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoClient(
            KakaoProperties properties,
            @Qualifier("kakaoAuthRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "kakaoAuth")
    public KakaoTokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", properties.getClientId());
        if (StringUtils.hasText(properties.getClientSecret())) {
            params.add("client_secret", properties.getClientSecret());
        }
        params.add("redirect_uri", properties.getRedirectUri());
        params.add("code", code);

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw authenticationException(
                    e.getStatusCode(),
                    ErrorCode.INVALID_AUTHORIZATION_CODE
            );
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
        }
    }

    @CircuitBreaker(name = "kakaoAuth")
    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        try {
            KakaoUserInfoResponse response = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw authenticationException(
                    e.getStatusCode(),
                    ErrorCode.KAKAO_AUTH_FAILED
            );
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
        }
    }

    private BusinessException authenticationException(
            HttpStatusCode status,
            ErrorCode clientError
    ) {
        return status.is4xxClientError()
                ? new BusinessException(clientError)
                : new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
    }
}
