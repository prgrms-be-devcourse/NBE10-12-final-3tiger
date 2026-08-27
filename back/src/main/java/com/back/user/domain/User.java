package com.back.user.domain;

import com.back.course.domain.Persona;
import com.back.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "\"user\"",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_email",
                        columnNames = "email"
                ),
                @UniqueConstraint(
                        name = "uq_user_provider",
                        columnNames = {"provider", "provider_uid"}
                )
        }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "persona_pref", nullable = false, length = 20)
    private Persona primaryPersona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "persona_tags", columnDefinition = "jsonb")
    private List<Persona> personaTags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_uid", length = 255)
    private String providerUid;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(
            String email,
            String passwordHash,
            String nickname,
            Provider provider,
            String providerUid
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.provider = provider;
        this.providerUid = providerUid;
        this.primaryPersona = Persona.walker;
    }

    public static User createLocal(
            String email,
            String passwordHash,
            String nickname
    ) {
        Objects.requireNonNull(email, "일반 회원의 이메일은 필수입니다.");
        Objects.requireNonNull(passwordHash, "일반 회원의 비밀번호 해시는 필수입니다.");
        Objects.requireNonNull(nickname, "닉네임은 필수입니다.");

        return new User(
                email,
                passwordHash,
                nickname,
                Provider.LOCAL,
                null
        );
    }

    public static User createKakao(
            String providerUid,
            String email,
            String nickname
    ) {
        Objects.requireNonNull(providerUid, "카카오 회원의 providerUid는 필수입니다.");
        Objects.requireNonNull(nickname, "닉네임은 필수입니다.");

        return new User(
                email,
                null,
                nickname,
                Provider.KAKAO,
                providerUid
        );
    }

    public void updateProfile(String nickname) {
        this.nickname = Objects.requireNonNull(
                nickname,
                "닉네임은 필수입니다."
        );
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void withdraw() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Persona getPrimaryPersona() {
        return primaryPersona;
    }

    public List<Persona> getPersonaTags() {
        return personaTags == null ? List.of() : List.copyOf(personaTags);
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderUid() {
        return providerUid;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
