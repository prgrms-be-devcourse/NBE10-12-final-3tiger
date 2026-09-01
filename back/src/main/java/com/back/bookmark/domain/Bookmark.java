package com.back.bookmark.domain;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookmark")
@IdClass(BookmarkId.class)
public class Bookmark {
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Id @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id")
    private Course course;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column
    private Integer rating;
    @Column(nullable = false)
    private int usageCount;
    @Column
    private LocalDateTime lastUsedAt;

    protected Bookmark() {}
    public Bookmark(User user, Course course) { this.user = user; this.course = course; this.createdAt = LocalDateTime.now(); }
    public Course getCourse() { return course; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Integer getRating() { return rating; }
    public int getUsageCount() { return usageCount; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void rate(int rating) { this.rating = rating; }
    public void recordUsage(LocalDateTime usedAt) {
        this.usageCount++;
        this.lastUsedAt = usedAt;
    }
}
