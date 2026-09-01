package com.back.place.kakao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoPlaceClientConfig {

    @Bean
    RestClient kakaoPlaceRestClient(
            RestClient.Builder builder,
            KakaoPlaceProperties properties
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "KakaoAK " + properties.getRestApiKey()
                )
                .requestFactory(requestFactory)
                .build();
    }
}
