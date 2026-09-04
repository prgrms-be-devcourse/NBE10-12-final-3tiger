package com.back.post.service;

import com.back.bookmark.repository.BookmarkRepository;
import com.back.global.api.PageResponse;
import com.back.comment.repository.CommentRepository;
import com.back.global.error.ApiException;
import com.back.notification.event.PostLikedEvent;
import com.back.post.domain.Post;
import com.back.post.domain.PostLike;
import com.back.post.repository.PostLikeRepository;
import com.back.post.repository.PostRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.userblock.service.UserBlockService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class PostLikeService {
    private final PostRepository posts; private final UserRepository users; private final PostLikeRepository postLikes;
    private final CommentRepository comments; private final PostLikeWriter postLikeWriter;
    private final BookmarkRepository bookmarks;
    private final ApplicationEventPublisher eventPublisher;
    private final UserBlockService userBlockService;
    public PostLikeService(PostRepository posts, UserRepository users, PostLikeRepository postLikes,
                           CommentRepository comments, PostLikeWriter postLikeWriter,
                           ApplicationEventPublisher eventPublisher, BookmarkRepository bookmarks,
                           UserBlockService userBlockService) {
        this.posts = posts; this.users = users; this.postLikes = postLikes; this.comments = comments;
        this.postLikeWriter = postLikeWriter; this.eventPublisher = eventPublisher;
        this.bookmarks = bookmarks;
        this.userBlockService = userBlockService;
    }

    @Transactional
    public LikeResult like(Long postId, Long userId) {
        Post post = getPostByIdOrThrow(postId);

        if (postLikes.existsByPost_IdAndUser_Id(postId, userId)) {
            return new LikeResult(true, post.getLikeCount());
        }

        if (userBlockService.isBlocked(userId, post.getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "차단한 사용자의 게시글에는 좋아요를 누를 수 없습니다.");
        }
        User user = getUserByIdOrThrow(userId);
        try {
            postLikeWriter.trySaveLike(post, user);
        } catch (DataIntegrityViolationException e) {
            // post_id+user_id 유니크 제약에 걸림 = 동시 요청이 먼저 저장을 끝냄 → 이미 좋아요 상태로 간주
            return new LikeResult(true, post.getLikeCount());
        }

        // increaseLikeCount(clearAutomatically=true)가 영속성 컨텍스트를 비우기 전에
        // 지연로딩되는 게시물 작성자 id를 미리 읽어둠 (이후엔 LazyInitializationException 위험)
        Long postOwnerId = post.getUser().getId();
        posts.increaseLikeCount(postId);
        eventPublisher.publishEvent(new PostLikedEvent(postOwnerId, userId, user.getNickname(), user.getProfileImageUrl(), postId));
        // JPQL 벌크 업데이트라 영속성 컨텍스트(post)에는 반영되지 않으므로 직접 +1 계산
        return new LikeResult(true, post.getLikeCount() + 1);
    }

    @Transactional
    public LikeResult unlike(Long postId, Long userId) {
        Post post = getPostByIdOrThrow(postId);

        int deleted = postLikes.deleteByPost_IdAndUser_Id(postId, userId);
        if (deleted == 0) {
            return new LikeResult(false, post.getLikeCount());
        }

        posts.decreaseLikeCount(postId);
        // JPQL 벌크 업데이트라 영속성 컨텍스트(post)에는 반영되지 않으므로 직접 -1 계산 (0 미만 방지)
        return new LikeResult(false, Math.max(post.getLikeCount() - 1, 0));
    }

    public PageResponse<LikedPostItem> myLikes(Long userId, int page, int size) {
        var found = postLikes.findByUser_IdOrderByCreatedAtDesc(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Long> postIds = found.getContent().stream()
                .map(postLike -> postLike.getPost().getId())
                .toList();
        Map<Long, Long> commentCounts = postIds.isEmpty()
                ? Map.of()
                : comments.countByPostIds(postIds).stream()
                    .collect(Collectors.toMap(
                        CommentRepository.PostCommentCount::getPostId,
                        CommentRepository.PostCommentCount::getCommentCount));
        List<Long> courseIds = found.getContent().stream()
                .map(postLike -> postLike.getPost().getCourse().getId())
                .distinct()
                .toList();
        Set<Long> bookmarkedCourseIds = courseIds.isEmpty()
                ? Set.of()
                : bookmarks.findBookmarkedCourseIds(userId, courseIds);
        return PageResponse.from(found.map(postLike -> toLikedPostItem(postLike, userId, commentCounts, bookmarkedCourseIds)));
    }

    private LikedPostItem toLikedPostItem(PostLike postLike, Long currentUserId, Map<Long, Long> commentCounts,
                                          Set<Long> bookmarkedCourseIds) {
        Post post = postLike.getPost();
        return new LikedPostItem(post.getId(), post.getCourse().getId(), post.getUser().getNickname(),
                post.getUser().getProfileImageUrl(), post.getContent(),
                post.getPhotoUrl(), post.getLikeCount(),
                commentCounts.getOrDefault(post.getId(), 0L),
                bookmarkedCourseIds.contains(post.getCourse().getId()),
                post.getUser().getId().equals(currentUserId), postLike.getCreatedAt());
    }

    private Post getPostByIdOrThrow(Long id) {
        return posts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
    }

    private User getUserByIdOrThrow(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
    }

    public record LikeResult(boolean isLiked, int likeCount) {}
    public record LikedPostItem(Long postId, Long courseId, String nickname, String profileImageUrl,
                                String content, String photoUrl,
                                int likeCount, long commentCount, boolean isBookmarked, boolean isMine,
                                LocalDateTime likedAt) {}
}
