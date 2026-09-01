package com.back.place.kakao;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.place.kakao.dto.KakaoPlaceSearchResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoPlaceClient {

    private final RestClient restClient;

    public KakaoPlaceClient(
            @Qualifier("kakaoPlaceRestClient")
            RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public KakaoPlaceSearchResponse search(String query, int size) {
        try {
            KakaoPlaceSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", Math.min(size, 15))
                            .build())
                    .retrieve()
                    .body(KakaoPlaceSearchResponse.class);

            if (response == null) {
                throw new BusinessException(
                        ErrorCode.KAKAO_PLACE_SEARCH_FAILED
                );
            }

            return response;
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.KAKAO_PLACE_SEARCH_FAILED
            );
        }
    }
}
