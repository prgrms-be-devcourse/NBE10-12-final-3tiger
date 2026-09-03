package com.back.map.kakao;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.kakao.dto.KakaoRouteDirectionsResponse;
import com.back.map.kakao.dto.KakaoTransitDirectionsResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Component
public class KakaoDirectionsClient {

    private final RestClient restClient;

    public KakaoDirectionsClient(
            @Qualifier("kakaoMapRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public KakaoRouteDirectionsResponse getWalk(
            double startLat, double startLng,
            double endLat, double endLng,
            String endName
    ) {
        return getRoute(
                "/v2/routing/walk",
                startLat, startLng, endLat, endLng, endName
        );
    }

    public KakaoRouteDirectionsResponse getBicycle(
            double startLat, double startLng,
            double endLat, double endLng,
            String endName
    ) {
        return getRoute(
                "/v2/routing/bicycle",
                startLat, startLng, endLat, endLng, endName
        );
    }

    public KakaoTransitDirectionsResponse getPublicTransit(
            double startLat, double startLng,
            double endLat, double endLng,
            String endName
    ) {
        return execute(() -> restClient.get()
                .uri(builder -> builder
                        .path("/v2/routing/publictraffic")
                        .queryParam("start_x", startLng)
                        .queryParam("start_y", startLat)
                        .queryParam("end_x", endLng)
                        .queryParam("end_y", endLat)
                        .queryParam("s_name", "현재 위치")
                        .queryParam("e_name", endName)
                        .build())
                .retrieve()
                .body(KakaoTransitDirectionsResponse.class));
    }

    private KakaoRouteDirectionsResponse getRoute(
            String path,
            double startLat, double startLng,
            double endLat, double endLng,
            String endName
    ) {
        return execute(() -> restClient.get()
                .uri(builder -> builder
                        .path(path)
                        .queryParam("start_x", startLng)
                        .queryParam("start_y", startLat)
                        .queryParam("end_x", endLng)
                        .queryParam("end_y", endLat)
                        .queryParam("s_name", "현재 위치")
                        .queryParam("e_name", endName)
                        .build())
                .retrieve()
                .body(KakaoRouteDirectionsResponse.class));
    }

    private <T> T execute(Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
            }
            return response;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.KAKAO_DIRECTIONS_FAILED);
        }
    }
}
