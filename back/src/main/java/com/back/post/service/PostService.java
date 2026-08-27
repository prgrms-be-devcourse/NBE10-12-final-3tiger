package com.back.post.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.comment.repository.CommentRepository;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.post.repository.PostLikeRepository;
import com.back.post.storage.PhotoStorage;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts; private final UserRepository users; private final CourseRepository courses;
    private final PostLikeRepository postLikes; private final CommentRepository comments;
    private final CommentUpvoteRepository commentUpvotes; private final PhotoStorage storage;
    public PostService(PostRepository posts, UserRepository users, CourseRepository courses,
                       PostLikeRepository postLikes, CommentRepository comments,
                       CommentUpvoteRepository commentUpvotes, PhotoStorage storage) {
        this.posts = posts; this.users = users; this.courses = courses;
        this.postLikes = postLikes; this.comments = comments;
        this.commentUpvotes = commentUpvotes; this.storage = storage;
    }
    public PageResponse<FeedItem> feed(Long userId, String regionCode, String sort, int page, int size) {
        Sort sorting = "popularity".equalsIgnoreCase(sort) ? Sort.by(Sort.Direction.DESC, "likeCount", "createdAt") : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sorting);
        Page<Post> found = regionCode == null || regionCode.isBlank() ? posts.findAll(pageable) : posts.findByCourseRegionCode(regionCode, pageable);
        List<Long> postIds = postIds(found);
        Map<Long, Long> commentCounts = commentCounts(postIds);
        Set<Long> likedPostIds = userId == null || postIds.isEmpty()
                ? Set.of()
                : Set.copyOf(postLikes.findLikedPostIds(userId, postIds));
        return PageResponse.from(found.map(post -> toFeedItem(post, commentCounts, likedPostIds)));
    }
    public PageResponse<MyPostItem> mine(Long userId, int page, int size) {
        Page<Post> found = posts.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, Long> commentCounts = commentCounts(postIds(found));
        return PageResponse.from(found
                .map(p -> new MyPostItem(p.getId(), p.getCourse().getId(), p.getTitle(), p.getContent(), p.getPhotoUrl(),
                        p.getLikeCount(), commentCounts.getOrDefault(p.getId(), 0L), p.getWalkedAt())));
    }
    public PhotoStorage.UploadTarget uploadUrl(Long userId, String fileName, String contentType) {
        return storage.createUploadTarget(userId, fileName, contentType);
    }
    @Transactional public CreatedPost create(Long userId, CreateCommand command) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
        Course course = courses.findById(command.courseId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        Post post = posts.save(new Post(user, course, command.title(), command.content(), command.photoUrl(), command.walkedAt()));
        return new CreatedPost(post.getId());
    }
    @Transactional public void delete(Long userId, Long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
        if (!post.getUser().getId().equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN, "본인의 게시물만 삭제할 수 있습니다.");
        commentUpvotes.deleteAllByPostId(postId);
        comments.deleteAllByPostId(postId);
        postLikes.deleteAllByPostId(postId);
        posts.delete(post);
    }
    private FeedItem toFeedItem(Post p, Map<Long, Long> commentCounts, Set<Long> likedPostIds) {
        return new FeedItem(p.getId(), p.getCourse().getId(), p.getUser().getNickname(), p.getTitle(), p.getContent(),
                p.getPhotoUrl(), p.getLikeCount(), commentCounts.getOrDefault(p.getId(), 0L), likedPostIds.contains(p.getId()), p.getWalkedAt());
    }
    private List<Long> postIds(Page<Post> found) {
        return found.getContent().stream().map(Post::getId).toList();
    }
    private Map<Long, Long> commentCounts(List<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return comments.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(CommentRepository.PostCommentCount::getPostId,
                        CommentRepository.PostCommentCount::getCommentCount));
    }
    public record CreateCommand(Long courseId, String title, String content, String photoUrl, LocalDateTime walkedAt) {}
    public record CreatedPost(Long postId) {}
    public record FeedItem(Long postId, Long courseId, String nickname, String title, String content, String photoUrl,
                           int likeCount, long commentCount, boolean isLiked, LocalDateTime walkedAt) {}
    public record MyPostItem(Long postId, Long courseId, String title, String content, String photoUrl,
                             int likeCount, long commentCount, LocalDateTime walkedAt) {}
}
