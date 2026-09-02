package com.back.place.kakao.dto;

public record PlaceSearchItem(
        String name,
        String address,
        String roadAddress,
        double latitude,
        double longitude,
        String category,
        String placeUrl,
        boolean supportedRegion
) {}
