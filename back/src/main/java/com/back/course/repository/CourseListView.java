package com.back.course.repository;

public interface CourseListView {
    Long getCourseId();
    String getName();
    Integer getDistanceM();
    Integer getEstimatedMinutes();
    Boolean getIsLoop();
    Double getStartLat();
    Double getStartLng();
    Double getFlatness();
    Double getShadeScore();
    Double getWheelchair();
    Integer getLikeCount();
}
