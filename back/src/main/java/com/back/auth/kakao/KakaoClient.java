package com.back.auth.kakao;

import com.back.auth.kakao.dto.KakaoTokenResponse;
import com.back.auth.kakao.dto.KakaoUserInfoResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoClient {

    private final KakaoProperties props;
    private final RestClient restClient;

    public KakaoClient(KakaoProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    public KakaoTokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", props.getClientId());
        if (StringUtils.hasText(props.getClientSecret())) {
            params.add("client_secret", props.getClientSecret());
        }
        params.add("redirect_uri", props.getRedirectUri());
        params.add("code", code);

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
        }
    }

    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        try {
            KakaoUserInfoResponse response = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED);
        }
    }
}