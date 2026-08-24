package com.back.post.domain;

import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_post_like")
@IdClass(PostLikeId.class)
public class PostLike {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "post_id") private Post post;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    protected PostLike() {}
    public PostLike(Post post, User user) { this.post = post; this.user = user; this.createdAt = LocalDateTime.now(); }
}
