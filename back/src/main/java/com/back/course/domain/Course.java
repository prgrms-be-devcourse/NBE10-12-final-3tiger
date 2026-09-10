package com.back.course.domain;

import com.back.course.map.domain.CourseMapImageStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "course")
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "region_code", nullable = false)
    private String regionCode;
    @Column(name = "distance_m", nullable = false)
    private int distanceM;
    @Column(name = "map_image_url", length = 2048)
    private String mapImageUrl;
    @Enumerated(EnumType.STRING)
    @Column(name = "map_image_status", length = 20)
    private CourseMapImageStatus mapImageStatus;

    protected Course() {}
    public Course(String name, String regionCode, int distanceM) {
        this.name = name; this.regionCode = regionCode; this.distanceM = distanceM;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRegionCode() { return regionCode; }
    public int getDistanceM() { return distanceM; }
    public String getMapImageUrl() { return mapImageUrl; }
    public CourseMapImageStatus getMapImageStatus() { return mapImageStatus; }
}
