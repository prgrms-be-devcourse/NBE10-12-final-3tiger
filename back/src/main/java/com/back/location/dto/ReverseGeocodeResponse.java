package com.back.location.dto;

public record ReverseGeocodeResponse(
        double latitude,
        double longitude,
        String roadAddress,
        String jibunAddress,
        String city,
        String district,
        String neighborhood,
        boolean supportedRegion
) {}
