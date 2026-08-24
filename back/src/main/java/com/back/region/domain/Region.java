package com.back.region.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(name = "region_code", length = 10, nullable = false)
    private String regionCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "course_count", nullable = false)
    private int courseCount;

    protected Region() {}

    public String getRegionCode() { return regionCode; }
    public String getName() { return name; }
    public int getCourseCount() { return courseCount; }
}
