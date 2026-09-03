package com.back.map.kakao;

import com.back.map.kakao.dto.KakaoRouteDirectionsResponse;
import com.back.map.kakao.dto.KakaoTransitDirectionsResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoDirectionsResponseDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesWalkingLegStepsAndAllPathPoints() throws Exception {
        String json = """
                {
                  "status": "OK",
                  "route": {
                    "properties": {
                      "totalDistance": 1840,
                      "totalTime": 1520,
                      "landingUrl": "https://map.kakao.com/link/by/walk/test"
                    },
                    "legs": [{
                      "properties": {"distance": 1840, "time": 1520},
                      "steps": [{
                        "properties": {
                          "distance": 93,
                          "guidance": "강서구청 방향으로 이동",
                          "time": 84,
                          "x": 126.8495,
                          "y": 37.5509
                        },
                        "path": {
                          "points": [
                            [126.8495, 37.5509],
                            [126.849531, 37.550972],
                            [126.849612, 37.551053]
                          ]
                        }
                      }]
                    }]
                  }
                }
                """;

        var response = objectMapper.readValue(json, KakaoRouteDirectionsResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.route().legs().getFirst().steps().getFirst().path().points())
                .containsExactly(
                        java.util.List.of(126.8495, 37.5509),
                        java.util.List.of(126.849531, 37.550972),
                        java.util.List.of(126.849612, 37.551053)
                );
    }

    @Test
    void deserializesTransitStepsVehiclesStopsAndLandingUrl() throws Exception {
        String json = """
                {
                  "status": "OK",
                  "properties": {
                    "total": 1,
                    "bus": 1,
                    "subway": 0,
                    "busAndSubway": 0,
                    "landingURL": "https://map.kakao.com/link/by/traffic/test"
                  },
                  "routes": [{
                    "properties": {
                      "type": "BUS",
                      "totalDistance": 5013,
                      "totalTime": 2115,
                      "transfers": 0,
                      "fare": {"value": 1450}
                    },
                    "steps": [{
                      "properties": {
                        "guidance": "6645번 버스 승차",
                        "type": "BUS",
                        "distance": 4127,
                        "time": 1158,
                        "stops": [{"name": "강서구청"}, {"name": "서울식물원"}],
                        "vehicles": [{"name": "6645", "type": "마을"}]
                      },
                      "path": {
                        "points": [
                          [126.8495, 37.5509],
                          [126.827658, 37.5667106]
                        ]
                      }
                    }]
                  }]
                }
                """;

        var response = objectMapper.readValue(json, KakaoTransitDirectionsResponse.class);
        var step = response.routes().getFirst().steps().getFirst();

        assertThat(response.properties().landingUrl())
                .isEqualTo("https://map.kakao.com/link/by/traffic/test");
        assertThat(step.properties().vehicles().getFirst().name()).isEqualTo("6645");
        assertThat(step.properties().stops())
                .extracting(KakaoTransitDirectionsResponse.Stop::name)
                .containsExactly("강서구청", "서울식물원");
        assertThat(step.path().points()).hasSize(2);
    }
}
