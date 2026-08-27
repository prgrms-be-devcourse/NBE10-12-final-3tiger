package com.back.post.domain;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_post")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "post_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id")
    private Course course;
    @Column(name = "caption", nullable = false, length = 500)
    private String content;
    @Column(name = "photo_url", length = 2048)
    private String photoUrl;
    @Column(nullable = false)
    private int likeCount;
    @Column(nullable = false)
    private LocalDateTime walkedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Post() {}
    public Post(User user, Course course, String content, String photoUrl, LocalDateTime walkedAt) {
        this.user = user; this.course = course; this.content = content; this.photoUrl = photoUrl;
        this.walkedAt = walkedAt; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; } public User getUser() { return user; } public Course getCourse() { return course; }
    public String getContent() { return content; }
    public String getPhotoUrl() { return photoUrl; }
    public int getLikeCount() { return likeCount; } public LocalDateTime getWalkedAt() { return walkedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void increaseLikeCount() { this.likeCount++; }
    public void decreaseLikeCount() { if (this.likeCount > 0) this.likeCount--; }
}
