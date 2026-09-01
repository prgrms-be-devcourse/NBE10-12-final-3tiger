package com.back.place.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.place.kakao")
@Getter
@Setter
public class KakaoPlaceProperties {
    private String restApiKey;
}
