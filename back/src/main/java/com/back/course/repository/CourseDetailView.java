package com.back.course.repository;

public interface CourseDetailView {
    Long getCourseId();
    String getName();
    String getPathGeoJson();
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
}
