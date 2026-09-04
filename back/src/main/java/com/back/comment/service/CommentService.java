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
import com.back.userblock.service.UserBlockService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository comments; private final CommentUpvoteRepository commentUpvotes;
    private final PostRepository posts; private final UserRepository users;
    private final CommentUpvoteWriter commentUpvoteWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final UserBlockService userBlockService;
    public CommentService(CommentRepository comments, CommentUpvoteRepository commentUpvotes, PostRepository posts,
                          UserRepository users, CommentUpvoteWriter commentUpvoteWriter,
                          ApplicationEventPublisher eventPublisher, UserBlockService userBlockService) {
        this.comments = comments; this.commentUpvotes = commentUpvotes; this.posts = posts; this.users = users;
        this.commentUpvoteWriter = commentUpvoteWriter;
        this.eventPublisher = eventPublisher;
        this.userBlockService = userBlockService;
    }

    // 빈 in 절을 만들지 않기 위한 sentinel (user id 는 항상 양수)
    private static final long NO_SUCH_USER_ID = -1L;

    public PageResponse<CommentResponse> getComments(Long postId, Long userId, String sort, Pageable pageable) {
        getPostByIdOrThrow(postId);
        Collection<Long> excludedUserIds = excludedUserIds(userId);
        // 원댓글만 sort 로 분기 (잘못된 값은 latest 로 폴백). 답글은 항상 createdAt ASC 유지
        Sort sorting = "upvote".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Order.desc("upvoteCount"), Sort.Order.desc("createdAt"))
                : Sort.by(Sort.Order.desc("createdAt"));
        Page<Comment> parents = comments.findVisibleParents(postId, excludedUserIds,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sorting));
        List<Long> parentIds = parents.getContent().stream().map(Comment::getId).toList();
        List<Comment> replies = parentIds.isEmpty()
                ? List.of()
                : comments.findVisibleReplies(parentIds, excludedUserIds);

        List<Long> allCommentIds = Stream.concat(parentIds.stream(), replies.stream().map(Comment::getId)).toList();
        Set<Long> upvotedCommentIds = userId == null || allCommentIds.isEmpty()
                ? Set.of()
                : Set.copyOf(commentUpvotes.findUpvotedCommentIds(userId, allCommentIds));

        Map<Long, List<Comment>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(reply -> reply.getParent().getId()));

        return PageResponse.from(parents.map(parent ->
                toCommentResponse(parent, repliesByParent.getOrDefault(parent.getId(), List.of()), upvotedCommentIds)));
    }

    @Transactional
    public Long createComment(Long postId, Long userId, String content) {
        Post post = getPostByIdOrThrow(postId);
        if (userBlockService.isBlocked(userId, post.getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "차단한 사용자의 게시글에는 댓글을 작성할 수 없습니다.");
        }
        User user = getUserByIdOrThrow(userId);
        Comment comment = comments.save(new Comment(post, user, content));
        eventPublisher.publishEvent(new CommentCreatedEvent(post.getUser().getId(), userId, user.getNickname(),
                user.getProfileImageUrl(), postId, comment.getId()));
        return comment.getId();
    }

    @Transactional
    public Long createReply(Long parentCommentId, Long userId, String content) {
        Comment parent = getCommentByIdOrThrow(parentCommentId);
        if (parent.isReply()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "답글에는 답글을 달 수 없습니다.");
        }
        if (userBlockService.isBlocked(userId, parent.getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "차단한 사용자의 댓글에는 답글을 작성할 수 없습니다.");
        }
        User user = getUserByIdOrThrow(userId);
        Comment reply = comments.save(new Comment(parent.getPost(), user, content, parent));
        // 답글 알림은 부모 댓글 작성자에게 (본인 댓글에 답글 시 리스너가 자기알림을 걸러냄)
        eventPublisher.publishEvent(new CommentCreatedEvent(parent.getUser().getId(), userId, user.getNickname(),
                user.getProfileImageUrl(), parent.getPost().getId(), reply.getId()));
        return reply.getId();
    }

    @Transactional
    public UpvoteResult toggleUpvote(Long commentId, Long userId) {
        Comment comment = getCommentByIdOrThrow(commentId);

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

        if (userBlockService.isBlocked(userId, comment.getUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "차단한 사용자의 댓글에는 공감할 수 없습니다.");
        }
        User user = getUserByIdOrThrow(userId);
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
        Comment comment = getCommentByIdOrThrow(commentId);

        if (!comment.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }

        // 답글은 항상 하드 삭제 / 원댓글은 하위 답글이 있으면 소프트 삭제, 없으면 하드 삭제
        if (!comment.isReply() && comments.existsByParent_Id(commentId)) {
            comment.softDelete();
            return;
        }
        comments.delete(comment);
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

    private Post getPostByIdOrThrow(Long id) {
        return posts.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
    }

    private User getUserByIdOrThrow(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }

    private Comment getCommentByIdOrThrow(Long id) {
        return comments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
    }

    private CommentResponse toCommentResponse(Comment comment, List<Comment> replies, Set<Long> upvotedCommentIds) {
        List<CommentResponse> replyResponses = replies.stream()
                .map(reply -> toCommentResponse(reply, List.of(), upvotedCommentIds))
                .toList();
        return new CommentResponse(comment.getId(), comment.getUser().getId(), comment.getUser().getNickname(),
                comment.getUser().getProfileImageUrl(), comment.getContent(), comment.getUpvoteCount(),
                upvotedCommentIds.contains(comment.getId()), comment.isDeleted(),
                comment.getCreatedAt(), replyResponses);
    }

    public record CommentResponse(Long commentId, Long userId, String nickname, String profileImageUrl,
                                  String content, int upvoteCount,
                                  boolean isUpvoted, boolean isDeleted, LocalDateTime createdAt,
                                  List<CommentResponse> replies) {}
    public record UpvoteResult(boolean upvoted, int upvoteCount) {}
}
