package com.back.place.kakao.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
  @ConfigurationProperties(prefix = "app.place.rate-limit")
  @Getter
  @Setter
  public class PlaceSearchRateLimitProperties {

      private long limit = 30;
      private Duration window = Duration.ofSeconds(60);
  }
