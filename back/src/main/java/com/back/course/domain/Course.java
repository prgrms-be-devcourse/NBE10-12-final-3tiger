package com.back.course.domain;

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

    protected Course() {}
    public Course(String name, String regionCode, int distanceM) {
        this.name = name; this.regionCode = regionCode; this.distanceM = distanceM;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRegionCode() { return regionCode; }
    public int getDistanceM() { return distanceM; }
}
