package com.back.place.naver;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class NaverTypoCorrectionClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaverTypoCorrectionClient(
            @Qualifier("naverApiHubRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String correct(String query) {
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/v1/errata")
                            .queryParam("query", query)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (responseBody == null) {
                throw new NaverTypoCorrectionException("네이버 오타변환 응답이 비어 있습니다.");
            }
            NaverTypoCorrectionResponse response = objectMapper.readValue(
                    responseBody,
                    NaverTypoCorrectionResponse.class
            );
            return response.errata();
        } catch (NaverTypoCorrectionException exception) {
            throw exception;
        } catch (RestClientException | JacksonException exception) {
            throw new NaverTypoCorrectionException(exception);
        }
    }
}
