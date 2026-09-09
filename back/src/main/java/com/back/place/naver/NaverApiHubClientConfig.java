package com.back.place.naver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NaverApiHubClientConfig {

    @Bean("naverApiHubRestClient")
    RestClient naverApiHubRestClient(NaverApiHubProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-NCP-APIGW-API-KEY-ID", properties.getClientId())
                .defaultHeader("X-NCP-APIGW-API-KEY", properties.getClientSecret())
                .requestFactory(requestFactory)
                .build();
    }
}
