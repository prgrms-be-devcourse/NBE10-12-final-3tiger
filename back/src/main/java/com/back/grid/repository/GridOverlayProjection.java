package com.back.grid.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface GridOverlayProjection {
    Long getGridId();
    String getRegionCode();
    Double getCentroidLat();
    Double getCentroidLng();
    BigDecimal getFlatness();
    BigDecimal getShadeSummer();
    BigDecimal getShadeWinterSun();
    BigDecimal getTrafficLow();
    BigDecimal getWheelchair();
    BigDecimal getSurfaceNatural();
    BigDecimal getBenchDensity();
    BigDecimal getRestroomProximity();
    BigDecimal getWaterFacility();
    String getDataVersion();
    Instant getUpdatedAt();
}
