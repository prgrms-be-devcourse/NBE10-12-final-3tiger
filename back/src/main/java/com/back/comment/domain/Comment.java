package com.back.comment.domain;

import com.back.post.domain.Post;
import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_comment")
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id")
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(nullable = false)
    private int upvoteCount;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Comment() {}
    public Comment(Post post, User user, String content) {
        this.post = post; this.user = user; this.content = content;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; } public Post getPost() { return post; } public User getUser() { return user; }
    public String getContent() { return content; } public int getUpvoteCount() { return upvoteCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void increaseUpvote() { this.upvoteCount++; }
    public void decreaseUpvote() { if (this.upvoteCount > 0) this.upvoteCount--; }
}
