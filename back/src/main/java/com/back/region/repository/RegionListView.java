package com.back.region.repository;

public interface RegionListView {
    String getRegionCode();
    String getName();
    double getCenterLat();
    double getCenterLng();
    int getCourseCount();
}
