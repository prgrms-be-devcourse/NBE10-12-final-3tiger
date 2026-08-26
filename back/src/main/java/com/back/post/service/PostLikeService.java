package com.back.post.service;

import com.back.global.api.PageResponse;
import com.back.comment.repository.CommentRepository;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.domain.PostLike;
import com.back.post.repository.PostLikeRepository;
import com.back.post.repository.PostRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PostLikeService {
    private final PostRepository posts; private final UserRepository users; private final PostLikeRepository postLikes;
    private final CommentRepository comments;
    public PostLikeService(PostRepository posts, UserRepository users, PostLikeRepository postLikes, CommentRepository comments) {
        this.posts = posts; this.users = users; this.postLikes = postLikes; this.comments = comments;
    }

    @Transactional
    public LikeResult like(Long postId, Long userId) {
        Post post = posts.findByIdForUpdate(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));

        if (postLikes.existsByPost_IdAndUser_Id(postId, userId)) {
            return new LikeResult(true, post.getLikeCount());
        }

        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
        postLikes.save(new PostLike(post, user));
        post.increaseLikeCount();
        return new LikeResult(true, post.getLikeCount());
    }

    @Transactional
    public LikeResult unlike(Long postId, Long userId) {
        Post post = posts.findByIdForUpdate(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));

        if (postLikes.existsByPost_IdAndUser_Id(postId, userId)) {
            postLikes.deleteByPost_IdAndUser_Id(postId, userId);
            post.decreaseLikeCount();
        }

        return new LikeResult(false, post.getLikeCount());
    }

    public PageResponse<LikedPostItem> myLikes(Long userId, int page, int size) {
        var found = postLikes.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<Long> postIds = found.getContent().stream().map(postLike -> postLike.getPost().getId()).toList();
        Map<Long, Long> commentCounts = postIds.isEmpty() ? Map.of() : comments.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(CommentRepository.PostCommentCount::getPostId,
                        CommentRepository.PostCommentCount::getCommentCount));
        return PageResponse.from(found.map(postLike -> toLikedPostItem(postLike, commentCounts)));
    }

    private LikedPostItem toLikedPostItem(PostLike postLike, Map<Long, Long> commentCounts) {
        Post post = postLike.getPost();
        return new LikedPostItem(post.getId(), post.getCourse().getId(), post.getUser().getNickname(), post.getTitle(), post.getContent(),
                post.getPhotoUrl(), post.getLikeCount(), commentCounts.getOrDefault(post.getId(), 0L), postLike.getCreatedAt());
    }

    public record LikeResult(boolean isLiked, int likeCount) {}
    public record LikedPostItem(Long postId, Long courseId, String nickname, String title, String content, String photoUrl,
                                int likeCount, long commentCount, LocalDateTime likedAt) {}
}
