package com.back.post.domain;

import java.io.Serializable;
import java.util.Objects;

public class PostLikeId implements Serializable {
    private Long post; private Long user;
    public PostLikeId() {}
    @Override public boolean equals(Object o) { return o instanceof PostLikeId other && Objects.equals(post, other.post) && Objects.equals(user, other.user); }
    @Override public int hashCode() { return Objects.hash(post, user); }
}
