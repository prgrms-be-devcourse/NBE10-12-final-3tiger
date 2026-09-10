package com.back.pushtoken.domain;

import com.back.global.entity.BaseEntity;
import com.back.user.domain.User;
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

/**
 * OS 레벨 푸시(FCM/Expo) 발송 대상이 되는 기기 토큰.
 * <p>
 * token 을 유니크 키로 삼는다(user 아님). 같은 기기에서 로그아웃 후 다른 계정으로 로그인하면
 * 동일 token 레코드의 소유자를 새 계정으로 옮겨, 항상 "현재 이 기기에 로그인된 사용자" 한 명에게만
 * 매핑되도록 한다. 이렇게 하지 않으면 로그아웃한 계정에도 푸시가 계속 발송된다.
 */
@Entity
@Table(name = "push_token", uniqueConstraints = @UniqueConstraint(
        name = "uk_push_token_token", columnNames = "token"
))
public class PushToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(nullable = false, length = 20)
    private String platform;

    protected PushToken() {
    }

    public PushToken(User user, String token, String platform) {
        this.user = user;
        this.token = token;
        this.platform = platform;
    }

    /** 이미 존재하는 기기 토큰의 소유자를 현재 로그인 사용자로 재지정한다. */
    public void reassignTo(User user, String platform) {
        this.user = user;
        this.platform = platform;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getToken() { return token; }
    public String getPlatform() { return platform; }
}
