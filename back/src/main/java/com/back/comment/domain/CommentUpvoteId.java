package com.back.comment.domain;

import java.io.Serializable;
import java.util.Objects;

public class CommentUpvoteId implements Serializable {
    private Long comment; private Long user;
    public CommentUpvoteId() {}
    @Override public boolean equals(Object o) { return o instanceof CommentUpvoteId other && Objects.equals(comment, other.comment) && Objects.equals(user, other.user); }
    @Override public int hashCode() { return Objects.hash(comment, user); }
}
