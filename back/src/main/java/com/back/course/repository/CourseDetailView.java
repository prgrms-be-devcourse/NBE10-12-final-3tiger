package com.back.course.repository;

public interface CourseDetailView {
    Long getCourseId();
    String getName();
    String getPathGeoJson();
    String getMapImageUrl();
    String getMapImageStatus();
    Integer getDistanceM();
    Integer getEstimatedMinutes();
    Integer getElevationGainM();
    Integer getElevationLossM();
    Boolean getIsLoop();
    String getSource();
    Double getFlatness();
    Double getShade();
    Double getSurfaceTemp();
    Double getAmenity();
    Double getScoreWalker();
    Double getScoreSenior();
    Double getScoreStroller();
    Double getScoreDog();
    String getSurfaceType();
    Double getSurfaceNatural();
    Double getBenchDensity();
    Double getRestroomProximity();
    Double getWaterFacility();
    Double getPavementQuality();
}
