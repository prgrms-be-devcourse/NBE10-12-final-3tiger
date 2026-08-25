package com.back.comment.domain;

import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_upvote")
@IdClass(CommentUpvoteId.class)
public class CommentUpvote {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "comment_id") private Comment comment;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    protected CommentUpvote() {}
    public CommentUpvote(Comment comment, User user) { this.comment = comment; this.user = user; this.createdAt = LocalDateTime.now(); }

    public Comment getComment() { return comment; }
    public User getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
