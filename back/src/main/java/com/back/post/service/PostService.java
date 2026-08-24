package com.back.post.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.post.storage.PhotoStorage;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts; private final UserRepository users; private final CourseRepository courses; private final PhotoStorage storage;
    public PostService(PostRepository posts, UserRepository users, CourseRepository courses, PhotoStorage storage) {
        this.posts = posts; this.users = users; this.courses = courses; this.storage = storage;
    }
    public PageResponse<FeedItem> feed(String regionCode, String sort, int page, int size) {
        Sort sorting = "popularity".equalsIgnoreCase(sort) ? Sort.by(Sort.Direction.DESC, "likeCount", "createdAt") : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sorting);
        Page<Post> found = regionCode == null || regionCode.isBlank() ? posts.findAll(pageable) : posts.findByCourseRegionCode(regionCode, pageable);
        return PageResponse.from(found.map(this::toFeedItem));
    }
    public PageResponse<MyPostItem> mine(Long userId, int page, int size) {
        return PageResponse.from(posts.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(p -> new MyPostItem(p.getId(), p.getCourse().getId(), p.getCaption(), p.getLikeCount(), p.getWalkedAt())));
    }
    public PhotoStorage.UploadTarget uploadUrl(String fileName, String contentType) { return storage.createUploadTarget(fileName, contentType); }
    @Transactional public CreatedPost create(Long userId, CreateCommand command) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
        Course course = courses.findById(command.courseId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        Post post = posts.save(new Post(user, course, command.caption(), command.photoUrl(), command.walkedAt()));
        return new CreatedPost(post.getId());
    }
    @Transactional public void delete(Long userId, Long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
        if (!post.getUser().getId().equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN, "본인의 게시물만 삭제할 수 있습니다.");
        posts.delete(post);
    }
    private FeedItem toFeedItem(Post p) { return new FeedItem(p.getId(), p.getCourse().getId(), p.getUser().getNickname(), p.getCaption(), p.getPhotoUrl(), p.getLikeCount(), p.getWalkedAt()); }
    public record CreateCommand(Long courseId, String caption, String photoUrl, LocalDateTime walkedAt) {}
    public record CreatedPost(Long postId) {}
    public record FeedItem(Long postId, Long courseId, String nickname, String caption, String photoUrl, int likeCount, LocalDateTime walkedAt) {}
    public record MyPostItem(Long postId, Long courseId, String caption, int likeCount, LocalDateTime walkedAt) {}
}
