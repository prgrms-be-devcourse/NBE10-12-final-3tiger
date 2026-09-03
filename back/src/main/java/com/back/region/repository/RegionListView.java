package com.back.region.repository;

public interface RegionListView {
    String getRegionCode();
    String getName();
    double getCenterLat();
    double getCenterLng();
    String getBbox();
    int getCourseCount();
}
