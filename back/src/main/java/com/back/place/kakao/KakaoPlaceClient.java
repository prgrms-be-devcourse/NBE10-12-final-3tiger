package com.back.place.kakao;

import com.back.place.kakao.dto.KakaoPlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KakaoPlaceClient {

    private final KakaoPlaceProperties properties;
    private final RestClient restClient = RestClient.create();

    public KakaoPlaceSearchResponse search(String query, int size) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("dapi.kakao.com")
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", Math.min(size, 15))
                        .build())
                .header("Authorization",
                        "KakaoAK " + properties.getRestApiKey())
                .retrieve()
                .body(KakaoPlaceSearchResponse.class);
    }
}
