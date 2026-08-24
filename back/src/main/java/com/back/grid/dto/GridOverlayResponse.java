package com.back.grid.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GridOverlayResponse(
        long gridId,
        String regionCode,
        double centroidLat,
        double centroidLng,
        BigDecimal flatness,
        BigDecimal shadeSummer,
        BigDecimal shadeWinterSun,
        BigDecimal trafficLow,
        BigDecimal wheelchair,
        BigDecimal surfaceNatural,
        BigDecimal benchDensity,
        BigDecimal restroomProximity,
        BigDecimal waterFacility,
        String dataVersion,
        OffsetDateTime updatedAt
) {
}
