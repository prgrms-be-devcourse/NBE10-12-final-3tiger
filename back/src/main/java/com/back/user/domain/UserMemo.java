package com.back.user.domain;

import com.back.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** 로그인 사용자가 다른 사용자에게만 보관하는 개인 태그와 메모. */
@Entity
@Table(name = "user_memo", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_memo_owner_target", columnNames = {"owner_user_id", "target_user_id"}
))
public class UserMemo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_memo_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User target;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> tags = new ArrayList<>();

    @Column(length = 1000)
    private String memo;

    protected UserMemo() {
    }

    public UserMemo(User owner, User target, List<String> tags, String memo) {
        this.owner = owner;
        this.target = target;
        update(tags, memo);
    }

    public void update(List<String> tags, String memo) {
        this.tags = new ArrayList<>(tags);
        this.memo = memo;
    }

    public Long getId() { return id; }
    public List<String> getTags() { return List.copyOf(tags); }
    public String getMemo() { return memo; }
}
