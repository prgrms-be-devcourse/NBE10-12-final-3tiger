package com.back.grid.repository;

import java.math.BigDecimal;

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
}
