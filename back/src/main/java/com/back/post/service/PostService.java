package com.back.post.service;

import com.back.bookmark.repository.BookmarkRepository;
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
import com.back.userblock.service.UserBlockService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts; private final UserRepository users; private final CourseRepository courses;
    private final PostLikeRepository postLikes; private final CommentRepository comments;
    private final BookmarkRepository bookmarks;
    private final CommentUpvoteRepository commentUpvotes; private final PhotoStorage storage;
    private final UserBlockService userBlockService;
    public PostService(PostRepository posts, UserRepository users, CourseRepository courses,
                       PostLikeRepository postLikes, CommentRepository comments,
                       CommentUpvoteRepository commentUpvotes, PhotoStorage storage,
                       BookmarkRepository bookmarks, UserBlockService userBlockService) {
        this.posts = posts; this.users = users; this.courses = courses;
        this.postLikes = postLikes; this.comments = comments;
        this.commentUpvotes = commentUpvotes; this.storage = storage;
        this.bookmarks = bookmarks;
        this.userBlockService = userBlockService;
    }

    // 빈 in 절을 만들지 않기 위한 sentinel (user id 는 항상 양수)
    private static final long NO_SUCH_USER_ID = -1L;
    public PageResponse<FeedItem> feed(Long userId, String sort, int page, int size, String keyword) {
        Sort sorting = "popularity".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "likeCount", "createdAt", "id")
                : Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Pageable pageable = PageRequest.of(page, size, sorting);
        Collection<Long> excludedUserIds = excludedUserIds(userId);
        Page<Post> found = StringUtils.hasText(keyword)
                ? posts.searchFeed(keyword.trim(), excludedUserIds, pageable)
                : posts.findFeed(excludedUserIds, pageable);
        List<Long> postIds = postIds(found);
        Map<Long, Long> commentCounts = commentCounts(postIds);
        Set<Long> likedPostIds = userId == null || postIds.isEmpty()
                ? Set.of()
                : Set.copyOf(postLikes.findLikedPostIds(userId, postIds));
        List<Long> courseIds = found.getContent().stream()
                .map(post -> post.getCourse().getId())
                .distinct()
                .toList();
        Set<Long> bookmarkedCourseIds = userId == null || courseIds.isEmpty()
                ? Set.of()
                : bookmarks.findBookmarkedCourseIds(userId, courseIds);
        return PageResponse.from(found.map(post -> toFeedItem(post, userId, commentCounts, likedPostIds, bookmarkedCourseIds)));
    }
    public PageResponse<MyPostItem> mine(Long userId, int page, int size) {
        Page<Post> found = posts.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<Long, Long> commentCounts = commentCounts(postIds(found));
        return PageResponse.from(found
                .map(p -> new MyPostItem(p.getId(), p.getCourse().getId(), p.getContent(), p.getPhotoUrl(),
                        p.getLikeCount(), commentCounts.getOrDefault(p.getId(), 0L), p.getWalkedAt())));
    }
    public PhotoStorage.UploadTarget uploadUrl(Long userId, String fileName, String contentType) {
        return storage.createUploadTarget(userId, fileName, contentType);
    }
    public void deleteUploadedPhoto(Long userId, String photoUrl) {
        storage.delete(userId, photoUrl);
    }
    @Transactional public CreatedPost create(Long userId, CreateCommand command) {
        User user = getUserByIdOrThrow(userId);
        Course course = getCourseByIdOrThrow(command.courseId());
        Post post = posts.save(new Post(user, course, command.content(), command.photoUrl(), command.walkedAt()));
        return new CreatedPost(post.getId());
    }
    @Transactional public void delete(Long userId, Long postId) {
        Post post = getPostByIdOrThrow(postId);
        if (!post.getUser().getId().equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN, "본인의 게시물만 삭제할 수 있습니다.");
        commentUpvotes.deleteAllByPostId(postId);
        comments.deleteAllByPostId(postId);
        postLikes.deleteAllByPostId(postId);
        posts.delete(post);
        storage.delete(userId, post.getPhotoUrl());
    }
    private FeedItem toFeedItem(Post p, Long currentUserId, Map<Long, Long> commentCounts, Set<Long> likedPostIds,
                                Set<Long> bookmarkedCourseIds) {
        return new FeedItem(p.getId(), p.getCourse().getId(), p.getTitle(), p.getUser().getId(), p.getUser().getNickname(), p.getUser().getProfileImageUrl(), p.getContent(),
                p.getPhotoUrl(), p.getLikeCount(), commentCounts.getOrDefault(p.getId(), 0L),
                likedPostIds.contains(p.getId()), bookmarkedCourseIds.contains(p.getCourse().getId()),
                currentUserId != null && p.getUser().getId().equals(currentUserId), p.getWalkedAt());
    }
    private Post getPostByIdOrThrow(Long id) {
        return posts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
    }
    private User getUserByIdOrThrow(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
    }
    private Course getCourseByIdOrThrow(Long id) {
        return courses.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
    }
    // 비로그인이면 차단 관계가 없다. 결과가 비면 sentinel 을 넣어 빈 in 절을 피한다.
    private Collection<Long> excludedUserIds(Long userId) {
        if (userId == null) {
            return List.of(NO_SUCH_USER_ID);
        }
        Set<Long> blocked = userBlockService.relatedUserIds(userId);
        if (blocked.isEmpty()) {
            return List.of(NO_SUCH_USER_ID);
        }
        return blocked;
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
    public record CreateCommand(Long courseId, String content, String photoUrl, LocalDateTime walkedAt) {}
    public record CreatedPost(Long postId) {}
    public record FeedItem(Long postId, Long courseId, String title, Long userId, String nickname, String profileImageUrl, String content, String photoUrl,
                           int likeCount, long commentCount, boolean isLiked, boolean isBookmarked,
                           boolean isMine, LocalDateTime walkedAt) {}
    public record MyPostItem(Long postId, Long courseId, String content, String photoUrl,
                             int likeCount, long commentCount, LocalDateTime walkedAt) {}
}
