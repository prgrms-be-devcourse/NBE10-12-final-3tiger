package com.back.comment.service;

import com.back.comment.domain.Comment;
import com.back.comment.repository.CommentRepository;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.notification.event.CommentCreatedEvent;
import com.back.notification.event.CommentUpvotedEvent;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository comments; private final CommentUpvoteRepository commentUpvotes;
    private final PostRepository posts; private final UserRepository users;
    private final CommentUpvoteWriter commentUpvoteWriter;
    private final ApplicationEventPublisher eventPublisher;
    public CommentService(CommentRepository comments, CommentUpvoteRepository commentUpvotes, PostRepository posts,
                          UserRepository users, CommentUpvoteWriter commentUpvoteWriter,
                          ApplicationEventPublisher eventPublisher) {
        this.comments = comments; this.commentUpvotes = commentUpvotes; this.posts = posts; this.users = users;
        this.commentUpvoteWriter = commentUpvoteWriter;
        this.eventPublisher = eventPublisher;
    }

    public PageResponse<CommentResponse> getComments(Long postId, Pageable pageable) {
        posts.findById(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
        return PageResponse.from(comments.findByPost_IdOrderByCreatedAtDesc(postId, pageable).map(this::toCommentResponse));
    }

    @Transactional
    public Long createComment(Long postId, Long userId, String content) {
        Post post = posts.findById(postId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));
        Comment comment = comments.save(new Comment(post, user, content));
        eventPublisher.publishEvent(new CommentCreatedEvent(post.getUser().getId(), userId, user.getNickname(),
                user.getProfileImageUrl(), postId, comment.getId()));
        return comment.getId();
    }

    @Transactional
    public UpvoteResult toggleUpvote(Long commentId, Long userId) {
        Comment comment = comments.findById(commentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));

        if (commentUpvotes.existsByComment_IdAndUser_Id(commentId, userId)) {
            int deleted = commentUpvotes.deleteByComment_IdAndUser_Id(commentId, userId);
            if (deleted == 0) {
                // 동시 요청이 먼저 취소를 끝냄 → 이미 공감 취소된 상태로 간주
                return new UpvoteResult(false, comment.getUpvoteCount());
            }
            comments.decreaseUpvote(commentId);
            // JPQL 벌크 업데이트라 영속성 컨텍스트(comment)에는 반영되지 않으므로 직접 -1 계산 (0 미만 방지)
            return new UpvoteResult(false, Math.max(comment.getUpvoteCount() - 1, 0));
        }

        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));
        try {
            commentUpvoteWriter.trySaveUpvote(comment, user);
        } catch (DataIntegrityViolationException e) {
            // comment_id+user_id 유니크 제약에 걸림 = 동시 요청이 먼저 저장을 끝냄 → 이미 공감한 상태로 간주
            return new UpvoteResult(true, comment.getUpvoteCount());
        }

        // increaseUpvote(clearAutomatically=true)가 영속성 컨텍스트를 비우기 전에
        // 지연로딩되는 댓글 작성자 id / 게시물 id를 미리 읽어둠 (이후엔 LazyInitializationException 위험)
        Long commentOwnerId = comment.getUser().getId();
        Long postId = comment.getPost().getId();
        comments.increaseUpvote(commentId);
        eventPublisher.publishEvent(new CommentUpvotedEvent(commentOwnerId, userId, user.getNickname(),
                user.getProfileImageUrl(), postId, commentId));
        // JPQL 벌크 업데이트라 영속성 컨텍스트(comment)에는 반영되지 않으므로 직접 +1 계산
        return new UpvoteResult(true, comment.getUpvoteCount() + 1);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = comments.findById(commentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));

        if (!comment.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }

        comments.delete(comment);
    }

    private CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getUser().getId(), comment.getUser().getNickname(),
                comment.getContent(), comment.getUpvoteCount(), comment.getCreatedAt());
    }

    public record CommentResponse(Long commentId, Long userId, String nickname, String content, int upvoteCount, LocalDateTime createdAt) {}
    public record UpvoteResult(boolean upvoted, int upvoteCount) {}
}
