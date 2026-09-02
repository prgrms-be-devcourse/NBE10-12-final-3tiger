package com.back.auth.google;

import com.back.auth.google.dto.GoogleTokenResponse;
import com.back.auth.google.dto.GoogleUserInfoResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleClient {

    private final GoogleProperties props;
    private final RestClient restClient;

    public GoogleClient(GoogleProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    public GoogleTokenResponse exchangeToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", props.getClientId());
        params.add("client_secret", props.getClientSecret());
        params.add("redirect_uri", props.getRedirectUri());
        params.add("code", code);

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new BusinessException(ErrorCode.INVALID_AUTHORIZATION_CODE);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
        }
    }

    public GoogleUserInfoResponse getUserInfo(String accessToken) {
        try {
            GoogleUserInfoResponse response = restClient.get()
                    .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
            }
            return response;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_SERVER_ERROR);
        }
    }
}