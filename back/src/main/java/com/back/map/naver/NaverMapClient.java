package com.back.map.naver;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.naver.dto.NaverReverseGeocodeResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NaverMapClient {

    private final RestClient restClient;

    public NaverMapClient(@Qualifier("naverMapRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public NaverReverseGeocodeResponse reverseGeocode(double latitude, double longitude) {
        try {
            NaverReverseGeocodeResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-reversegeocode/v2/gc")
                            .queryParam("coords", longitude + "," + latitude)
                            .queryParam("orders", "roadaddr,addr")
                            .queryParam("output", "json")
                            .build())
                    .retrieve()
                    .body(NaverReverseGeocodeResponse.class);

            if (response == null || response.status() == null || response.status().code() != 0) {
                throw new BusinessException(ErrorCode.NAVER_REVERSE_GEOCODING_FAILED);
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.NAVER_REVERSE_GEOCODING_FAILED);
        }
    }
}
