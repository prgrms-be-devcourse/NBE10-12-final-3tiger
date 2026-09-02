package com.back.auth.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kakao")
@Getter
@Setter
public class KakaoProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
