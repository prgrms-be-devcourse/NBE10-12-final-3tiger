package com.back.course.navigation.repository;

public interface CourseNavigationView {

    Long getCourseId();

    String getName();

    Integer getDistanceM();

    Integer getEstimatedMinutes();

    Boolean getIsLoop();

    Double getStartLat();

    Double getStartLng();

    Double getEndLat();

    Double getEndLng();

    String getPathGeoJson();

    Integer getCoordinateCount();

    Integer getSrid();

    String getGeometryType();

    Boolean getPathValid();

    Boolean getPathEmpty();

    Double getCalculatedDistanceM();

    Double getStartEndDistanceM();

}
