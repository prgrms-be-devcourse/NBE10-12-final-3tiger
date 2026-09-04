package com.back.comment.domain;

import com.back.post.domain.Post;
import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_comment")
public class Comment {
    public static final String DELETED_CONTENT = "삭제된 댓글입니다.";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id")
    private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    // 원댓글이면 null. 답글 목록은 CommentRepository.findByParent_IdInOrderByCreatedAtAsc 로 별도 조회한다
    // (자기참조 양방향 @OneToMany 매핑은 사용처가 없어 두지 않음)
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private Comment parent;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(nullable = false)
    private int upvoteCount;
    @Column(nullable = false)
    private boolean deleted;
    // 신고 누적 임계치 도달 시 자동 숨김 처리된다. 댓글 목록 조회에서 제외된다.
    @Column(nullable = false)
    private boolean hidden;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Comment() {}
    public Comment(Post post, User user, String content) {
        this(post, user, content, null);
    }
    public Comment(Post post, User user, String content, Comment parent) {
        // depth 1까지만 허용: 부모가 이미 답글이면 답글을 달 수 없음
        if (parent != null && parent.isReply()) {
            throw new IllegalArgumentException("답글에는 답글을 달 수 없습니다.");
        }
        this.post = post; this.user = user; this.content = content; this.parent = parent;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; } public Post getPost() { return post; } public User getUser() { return user; }
    public Comment getParent() { return parent; }
    public String getContent() { return content; } public int getUpvoteCount() { return upvoteCount; }
    public boolean isDeleted() { return deleted; }
    public boolean isHidden() { return hidden; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isReply() { return parent != null; }

    // 하위 답글이 있는 원댓글 삭제 시: 내용만 가리고 행은 유지 (답글은 그대로 노출)
    public void softDelete() {
        this.deleted = true;
        this.content = DELETED_CONTENT;
    }

    public void increaseUpvote() { this.upvoteCount++; }
    public void decreaseUpvote() { if (this.upvoteCount > 0) this.upvoteCount--; }
}
