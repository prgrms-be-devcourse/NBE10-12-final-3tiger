package com.back.course.map.storage;

public interface CourseMapImageStorage {

    String upload(Long courseId, byte[] image);
}
