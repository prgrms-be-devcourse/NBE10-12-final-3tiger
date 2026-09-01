package com.back.place.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoPlaceSearchResponse(
        List<Document> documents,
        Meta meta
) {
    public record Document(
            @JsonProperty("place_name")
            String placeName,

            @JsonProperty("address_name")
            String addressName,

            @JsonProperty("road_address_name")
            String roadAddressName,

            String x,
            String y,

            @JsonProperty("category_name")
            String categoryName,

            @JsonProperty("place_url")
            String placeUrl
    ) {}

    public record Meta(
            @JsonProperty("total_count")
            int totalCount
    ) {}
}
