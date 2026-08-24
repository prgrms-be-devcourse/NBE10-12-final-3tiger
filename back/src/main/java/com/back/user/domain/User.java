package com.back.user.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String nickname;

    protected User() {}
    public User(String nickname) { this.nickname = nickname; }
    public Long getId() { return id; }
    public String getNickname() { return nickname; }
}
