package com.back.grid.dto;

import java.math.BigDecimal;

public record GridOverlayResponse(
        long gridId,
        String regionCode,
        double centroidLat,
        double centroidLng,
        BigDecimal flatness,
        BigDecimal shadeSummer,
        BigDecimal shadeWinterSun,
        BigDecimal shadeNow,
        BigDecimal trafficLow,
        BigDecimal wheelchair,
        BigDecimal surfaceNatural,
        BigDecimal benchDensity,
        BigDecimal restroomProximity,
        BigDecimal waterFacility
) {
}
