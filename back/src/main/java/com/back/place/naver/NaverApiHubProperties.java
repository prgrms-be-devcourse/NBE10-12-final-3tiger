package com.back.place.naver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.place.naver-api-hub")
@Getter
@Setter
public class NaverApiHubProperties {

    private String baseUrl = "https://naverapihub.apigw.ntruss.com";
    private String clientId = "";
    private String clientSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(2);
}
