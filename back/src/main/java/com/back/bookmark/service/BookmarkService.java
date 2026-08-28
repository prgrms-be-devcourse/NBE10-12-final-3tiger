package com.back.bookmark.service;

import com.back.bookmark.domain.Bookmark;
import com.back.bookmark.repository.BookmarkRepository;
import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class BookmarkService {
    private final BookmarkRepository bookmarks; private final UserRepository users; private final CourseRepository courses;
    public BookmarkService(BookmarkRepository bookmarks, UserRepository users, CourseRepository courses) {
        this.bookmarks = bookmarks; this.users = users; this.courses = courses;
    }

    @Transactional
    public BookmarkState add(Long userId, Long courseId) {
        Course course = courses.findById(courseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
        if (!bookmarks.existsByUserIdAndCourseId(userId, courseId)) bookmarks.save(new Bookmark(user, course));
        return new BookmarkState(true);
    }

    @Transactional
    public BookmarkState remove(Long userId, Long courseId) {
        if (bookmarks.deleteByUserIdAndCourseId(userId, courseId) == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "저장되어 있지 않은 코스입니다.");
        return new BookmarkState(false);
    }

    public PageResponse<BookmarkItem> mine(Long userId, int page, int size) {
        var result = bookmarks.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(b -> new BookmarkItem(b.getCourse().getId(), b.getCourse().getName(), b.getCourse().getDistanceM(),
                        true, b.getCreatedAt()));
        return PageResponse.from(result);
    }

    public record BookmarkState(boolean isBookmarked) {}
    public record BookmarkItem(Long courseId, String name, int distanceM, boolean isBookmarked,
                               LocalDateTime bookmarkedAt) {}
}
