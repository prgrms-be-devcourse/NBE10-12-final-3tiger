package com.back.map.naver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.map.naver")
@Getter
@Setter
public class NaverMapProperties {

    private String baseUrl = "https://naveropenapi.apigw.ntruss.com";
    private String clientId = "";
    private String clientSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
}
