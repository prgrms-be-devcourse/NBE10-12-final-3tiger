package com.back.bookmark.domain;

import java.io.Serializable;
import java.util.Objects;

public class BookmarkId implements Serializable {
    private Long user;
    private Long course;
    public BookmarkId() {}
    public BookmarkId(Long user, Long course) { this.user = user; this.course = course; }
    @Override public boolean equals(Object o) { return o instanceof BookmarkId other && Objects.equals(user, other.user) && Objects.equals(course, other.course); }
    @Override public int hashCode() { return Objects.hash(user, course); }
}
