package com.back.notification.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    @Column(name = "actor_id", nullable = false)
    private Long actorId;
    @Column(name = "actor_nickname", nullable = false, length = 50)
    private String actorNickname;
    @Column(name = "actor_profile_image_url", length = 2048)
    private String actorProfileImageUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;
    @Column(name = "post_id", nullable = false)
    private Long postId;
    @Column(name = "comment_id")
    private Long commentId;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {}
    public Notification(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl,
                        NotificationType type, Long postId, Long commentId) {
        this.receiverId = receiverId; this.actorId = actorId; this.actorNickname = actorNickname;
        this.actorProfileImageUrl = actorProfileImageUrl; this.type = type; this.postId = postId; this.commentId = commentId;
        this.read = false; this.createdAt = LocalDateTime.now();
    }

    public void markAsRead() { this.read = true; }

    public Long getId() { return id; } public Long getReceiverId() { return receiverId; } public Long getActorId() { return actorId; }
    public String getActorNickname() { return actorNickname; } public String getActorProfileImageUrl() { return actorProfileImageUrl; }
    public NotificationType getType() { return type; } public Long getPostId() { return postId; } public Long getCommentId() { return commentId; }
    public boolean isRead() { return read; } public LocalDateTime getCreatedAt() { return createdAt; }
}
