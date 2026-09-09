package com.back.map.naver;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NaverStaticMapClient {

    public static final int IMAGE_WIDTH = 800;
    public static final int IMAGE_HEIGHT = 500;

    private final RestClient restClient;

    public NaverStaticMapClient(@Qualifier("naverMapRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public byte[] getMapImage(double centerLatitude, double centerLongitude, int level) {
        try {
            byte[] image = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-static/v2/raster")
                            .queryParam("crs", "EPSG:4326")
                            .queryParam("w", IMAGE_WIDTH)
                            .queryParam("h", IMAGE_HEIGHT)
                            .queryParam("scale", 1)
                            .queryParam("format", "png")
                            .queryParam("center", centerLongitude + "," + centerLatitude)
                            .queryParam("level", level)
                            .build())
                    .retrieve()
                    .body(byte[].class);
            if (image == null || image.length == 0) {
                throw new IllegalStateException("네이버 Static Map 응답이 비어 있습니다.");
            }
            return image;
        } catch (RestClientException exception) {
            throw new IllegalStateException("네이버 Static Map을 조회할 수 없습니다.", exception);
        }
    }
}
