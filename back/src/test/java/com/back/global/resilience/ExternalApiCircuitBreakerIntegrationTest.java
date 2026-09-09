package com.back.global.resilience;

import com.back.place.naver.NaverTypoCorrectionClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExternalApiCircuitBreakerIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private NaverTypoCorrectionClient typoCorrectionClient;

    @Test
    void returnsEmptyCorrectionWithoutExternalCallWhenCircuitIsOpen() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry
                .circuitBreaker("naverTypoCorrection");
        circuitBreaker.transitionToOpenState();

        try {
            assertThat(typoCorrectionClient.correct("spdlqj")).isEmpty();
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        } finally {
            circuitBreaker.reset();
        }
    }
}
