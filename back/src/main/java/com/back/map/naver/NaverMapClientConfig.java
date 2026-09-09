package com.back.map.naver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NaverMapClientConfig {

    @Bean("naverMapRestClient")
    RestClient naverMapRestClient(NaverMapProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("x-ncp-apigw-api-key-id", properties.getClientId())
                .defaultHeader("x-ncp-apigw-api-key", properties.getClientSecret())
                .requestFactory(requestFactory)
                .build();
    }
}
