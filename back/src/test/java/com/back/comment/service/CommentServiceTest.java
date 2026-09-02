package com.back.comment.service;

import com.back.comment.domain.Comment;
import com.back.comment.repository.CommentRepository;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.course.domain.Course;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.notification.event.CommentCreatedEvent;
import com.back.notification.event.CommentUpvotedEvent;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private CommentUpvoteRepository commentUpvoteRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentUpvoteWriter commentUpvoteWriter;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommentService commentService;

    private Post newPost() {
        User user = User.createLocal("test@test.com", "dummy-hash", "산책러");
        Course course = new Course("서울숲 코스", "11200", 2500);
        return new Post(user, course, "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
    }

    @Test
    @DisplayName("t1: 댓글 작성 성공 시 commentId를 반환하고 CommentCreatedEvent를 발행한다")
    void t1() {
        // given
        Long postId = 1L;
        Long userId = 1L;
        User postAuthor = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(postAuthor, "id", 42L);
        Course course = new Course("서울숲 코스", "11200", 2500);
        Post post = new Post(postAuthor, course, "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
        User commenter = User.createLocal("test@test.com", "dummy-hash", "산책러");
        ReflectionTestUtils.setField(commenter, "id", userId);
        Comment savedComment = new Comment(post, commenter, "좋은 코스네요");
        ReflectionTestUtils.setField(savedComment, "id", 100L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.findById(userId)).willReturn(Optional.of(commenter));
        given(commentRepository.save(any(Comment.class))).willReturn(savedComment);

        // when
        Long commentId = commentService.createComment(postId, userId, "좋은 코스네요");

        // then
        assertThat(commentId).isEqualTo(100L);

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CommentCreatedEvent event = captor.getValue();
        assertThat(event.receiverId()).isEqualTo(42L);
        assertThat(event.actorId()).isEqualTo(userId);
        assertThat(event.actorNickname()).isEqualTo("산책러");
        assertThat(event.actorProfileImageUrl()).isEqualTo(commenter.getProfileImageUrl());
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.commentId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("t2: 존재하지 않는 게시물에 댓글 작성 시 404 ApiException이 발생한다")
    void t2() {
        // given
        Long postId = 999L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.createComment(postId, userId, "내용"), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시물입니다.");
    }

    @Test
    @DisplayName("t3: 댓글 목록 조회 시 PageResponse로 필드가 정상 매핑된다")
    void t3() {
        // given
        Long postId = 1L;
        Post post = newPost();
        User user = User.createLocal("test@test.com", "dummy-hash", "산책러");
        ReflectionTestUtils.setField(user, "id", 5L);
        user.changeProfileImage("https://cdn.example.com/profile.jpg");
        Comment comment = new Comment(post, user, "좋은 코스네요");
        ReflectionTestUtils.setField(comment, "id", 10L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(comment)));
        given(commentRepository.findByParent_IdInOrderByCreatedAtAsc(List.of(10L)))
                .willReturn(List.of());

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, null, "latest", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
        CommentService.CommentResponse item = response.content().get(0);
        assertThat(item.commentId()).isEqualTo(10L);
        assertThat(item.userId()).isEqualTo(5L);
        assertThat(item.nickname()).isEqualTo("산책러");
        assertThat(item.profileImageUrl()).isEqualTo("https://cdn.example.com/profile.jpg");
        assertThat(item.content()).isEqualTo("좋은 코스네요");
        assertThat(item.upvoteCount()).isEqualTo(0);
        assertThat(item.isUpvoted()).isFalse();
        assertThat(item.isDeleted()).isFalse();
        assertThat(item.replies()).isEmpty();
        assertThat(item.createdAt()).isEqualTo(comment.getCreatedAt());
    }

    @Test
    @DisplayName("t4: 존재하지 않는 게시물의 댓글 목록 조회 시 404 ApiException이 발생한다")
    void t4() {
        // given
        Long postId = 999L;
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.getComments(postId, null, "latest", PageRequest.of(0, 20)), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시물입니다.");
    }

    @Test
    @DisplayName("t5: 댓글 공감 성공 시 upvoted=true이고 upvoteCount가 1 증가하며 CommentUpvotedEvent를 발행한다")
    void t5() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", 42L);
        Course course = new Course("서울숲 코스", "11200", 2500);
        Post post = new Post(author, course, "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", 7L);
        Comment comment = new Comment(post, author, "좋은 코스네요");
        ReflectionTestUtils.setField(comment, "id", commentId);
        User upvoter = User.createLocal("test@test.com", "dummy-hash", "산책러");
        ReflectionTestUtils.setField(upvoter, "id", userId);
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentUpvoteRepository.existsByComment_IdAndUser_Id(commentId, userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(upvoter));

        // when
        CommentService.UpvoteResult result = commentService.toggleUpvote(commentId, userId);

        // then
        assertThat(result.upvoted()).isTrue();
        assertThat(result.upvoteCount()).isEqualTo(1);
        verify(commentUpvoteWriter).trySaveUpvote(eq(comment), any(User.class));
        verify(commentRepository).increaseUpvote(commentId);

        ArgumentCaptor<CommentUpvotedEvent> captor = ArgumentCaptor.forClass(CommentUpvotedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        CommentUpvotedEvent event = captor.getValue();
        assertThat(event.receiverId()).isEqualTo(42L);
        assertThat(event.actorId()).isEqualTo(userId);
        assertThat(event.actorNickname()).isEqualTo("산책러");
        assertThat(event.actorProfileImageUrl()).isEqualTo(upvoter.getProfileImageUrl());
        assertThat(event.postId()).isEqualTo(7L);
        assertThat(event.commentId()).isEqualTo(commentId);
    }

    @Test
    @DisplayName("t6: 이미 공감한 댓글에 다시 요청하면 토글되어 upvoted=false이고 upvoteCount가 1 감소한다")
    void t6() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment comment = new Comment(post, author, "좋은 코스네요");
        comment.increaseUpvote();
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentUpvoteRepository.existsByComment_IdAndUser_Id(commentId, userId)).willReturn(true);
        given(commentUpvoteRepository.deleteByComment_IdAndUser_Id(commentId, userId)).willReturn(1);

        // when
        CommentService.UpvoteResult result = commentService.toggleUpvote(commentId, userId);

        // then
        assertThat(result.upvoted()).isFalse();
        assertThat(result.upvoteCount()).isEqualTo(0);
        verify(commentUpvoteRepository).deleteByComment_IdAndUser_Id(commentId, userId);
        verify(commentRepository).decreaseUpvote(commentId);
        verify(userRepository, never()).findById(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("t7: 존재하지 않는 댓글에 공감 요청 시 404 ApiException이 발생한다")
    void t7() {
        // given
        Long commentId = 999L;
        Long userId = 1L;
        given(commentRepository.findById(commentId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.toggleUpvote(commentId, userId), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 댓글입니다.");
    }

    @Test
    @DisplayName("t8: 본인 댓글 삭제에 성공한다")
    void t8() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", userId);
        Comment comment = new Comment(post, author, "좋은 코스네요");
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));

        // when
        commentService.deleteComment(commentId, userId);

        // then
        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("t9: 본인 댓글이 아니면 삭제 시 403 ApiException이 발생한다")
    void t9() {
        // given
        Long commentId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", 1L);
        Comment comment = new Comment(post, author, "좋은 코스네요");
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.deleteComment(commentId, 2L), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("본인 댓글만 삭제할 수 있습니다.");
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("t10: 존재하지 않는 댓글 삭제 시 404 ApiException이 발생한다")
    void t10() {
        // given
        Long commentId = 999L;
        Long userId = 1L;
        given(commentRepository.findById(commentId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.deleteComment(commentId, userId), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 댓글입니다.");
    }

    @Test
    @DisplayName("t11: 동시 요청으로 유니크 제약에 걸리면(DataIntegrityViolationException) 이미 공감한 상태로 간주하고 upvoteCount를 그대로 반환한다")
    void t11() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment comment = new Comment(post, author, "좋은 코스네요");
        for (int i = 0; i < 3; i++) comment.increaseUpvote();
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentUpvoteRepository.existsByComment_IdAndUser_Id(commentId, userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(User.createLocal("test@test.com", "dummy-hash", "산책러")));
        willThrow(new DataIntegrityViolationException("duplicate key")).given(commentUpvoteWriter).trySaveUpvote(any(), any());

        // when
        CommentService.UpvoteResult result = commentService.toggleUpvote(commentId, userId);

        // then
        assertThat(result.upvoted()).isTrue();
        assertThat(result.upvoteCount()).isEqualTo(3);
        verify(commentRepository, never()).increaseUpvote(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("t12: 공감 취소 시 이미 삭제되어 삭제 건수가 0이면 카운트를 감소시키지 않는다")
    void t12() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment comment = new Comment(post, author, "좋은 코스네요");
        for (int i = 0; i < 2; i++) comment.increaseUpvote();
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentUpvoteRepository.existsByComment_IdAndUser_Id(commentId, userId)).willReturn(true);
        given(commentUpvoteRepository.deleteByComment_IdAndUser_Id(commentId, userId)).willReturn(0);

        // when
        CommentService.UpvoteResult result = commentService.toggleUpvote(commentId, userId);

        // then
        assertThat(result.upvoted()).isFalse();
        assertThat(result.upvoteCount()).isEqualTo(2);
        verify(commentRepository, never()).decreaseUpvote(any());
    }

    @Test
    @DisplayName("t13: 로그인 사용자의 댓글 목록 조회 시 공감한 댓글은 isUpvoted=true, 안 한 댓글은 false")
    void t13() {
        // given
        Long postId = 1L;
        Long userId = 7L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment upvoted = new Comment(post, author, "내가 공감한 댓글");
        ReflectionTestUtils.setField(upvoted, "id", 10L);
        Comment notUpvoted = new Comment(post, author, "공감 안 한 댓글");
        ReflectionTestUtils.setField(notUpvoted, "id", 20L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(upvoted, notUpvoted)));
        given(commentRepository.findByParent_IdInOrderByCreatedAtAsc(List.of(10L, 20L)))
                .willReturn(List.of());
        given(commentUpvoteRepository.findUpvotedCommentIds(userId, List.of(10L, 20L)))
                .willReturn(List.of(10L));

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, userId, "latest", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).commentId()).isEqualTo(10L);
        assertThat(response.content().get(0).isUpvoted()).isTrue();
        assertThat(response.content().get(1).commentId()).isEqualTo(20L);
        assertThat(response.content().get(1).isUpvoted()).isFalse();
    }

    @Test
    @DisplayName("t14: 비로그인(userId=null) 댓글 목록 조회 시 모든 댓글 isUpvoted=false이고 공감 조회를 하지 않는다")
    void t14() {
        // given
        Long postId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment c1 = new Comment(post, author, "댓글1");
        ReflectionTestUtils.setField(c1, "id", 10L);
        Comment c2 = new Comment(post, author, "댓글2");
        ReflectionTestUtils.setField(c2, "id", 20L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(c1, c2)));
        given(commentRepository.findByParent_IdInOrderByCreatedAtAsc(List.of(10L, 20L)))
                .willReturn(List.of());

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, null, "latest", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).allMatch(item -> !item.isUpvoted());
        verify(commentUpvoteRepository, never()).findUpvotedCommentIds(any(), any());
    }

    @Test
    @DisplayName("t15: 원댓글에 답글 작성 성공 시 replyId를 반환하고 부모 댓글 작성자에게 CommentCreatedEvent를 발행한다")
    void t15() {
        // given
        Long parentCommentId = 10L;
        Long userId = 1L;
        User parentAuthor = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(parentAuthor, "id", 42L);
        Course course = new Course("서울숲 코스", "11200", 2500);
        Post post = new Post(parentAuthor, course, "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", 7L);
        Comment parent = new Comment(post, parentAuthor, "원댓글");
        ReflectionTestUtils.setField(parent, "id", parentCommentId);
        User replier = User.createLocal("reply@test.com", "dummy-hash", "답글러");
        ReflectionTestUtils.setField(replier, "id", userId);
        Comment savedReply = new Comment(post, replier, "답글입니다", parent);
        ReflectionTestUtils.setField(savedReply, "id", 200L);
        given(commentRepository.findById(parentCommentId)).willReturn(Optional.of(parent));
        given(userRepository.findById(userId)).willReturn(Optional.of(replier));
        given(commentRepository.save(any(Comment.class))).willReturn(savedReply);

        // when
        Long replyId = commentService.createReply(parentCommentId, userId, "답글입니다");

        // then
        assertThat(replyId).isEqualTo(200L);

        ArgumentCaptor<Comment> savedCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isReply()).isTrue();
        assertThat(savedCaptor.getValue().getParent()).isSameAs(parent);

        ArgumentCaptor<CommentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CommentCreatedEvent event = eventCaptor.getValue();
        assertThat(event.receiverId()).isEqualTo(42L);
        assertThat(event.actorId()).isEqualTo(userId);
        assertThat(event.actorNickname()).isEqualTo("답글러");
        assertThat(event.postId()).isEqualTo(7L);
        assertThat(event.commentId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("t16: 답글에 답글 작성 시 400 ApiException이 발생하고 저장/이벤트 발행을 하지 않는다")
    void t16() {
        // given
        Long parentReplyId = 20L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment original = new Comment(post, author, "원댓글");
        Comment parentReply = new Comment(post, author, "답글", original);
        ReflectionTestUtils.setField(parentReply, "id", parentReplyId);
        given(commentRepository.findById(parentReplyId)).willReturn(Optional.of(parentReply));

        // when
        ApiException exception = catchThrowableOfType(
                () -> commentService.createReply(parentReplyId, userId, "답답글"), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("답글에는 답글을 달 수 없습니다.");
        verify(commentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("t17: 존재하지 않는 부모 댓글에 답글 작성 시 404 ApiException이 발생한다")
    void t17() {
        // given
        Long parentCommentId = 999L;
        given(commentRepository.findById(parentCommentId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(
                () -> commentService.createReply(parentCommentId, 1L, "답글"), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 댓글입니다.");
    }

    @Test
    @DisplayName("t18: 하위 답글이 있는 원댓글 삭제 시 소프트 삭제된다 (content 변경, deleted=true, 하드 삭제 안 함)")
    void t18() {
        // given
        Long commentId = 10L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", userId);
        Comment comment = new Comment(post, author, "원댓글 내용");
        ReflectionTestUtils.setField(comment, "id", commentId);
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentRepository.existsByParent_Id(commentId)).willReturn(true);

        // when
        commentService.deleteComment(commentId, userId);

        // then
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isEqualTo("삭제된 댓글입니다.");
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("t19: 하위 답글이 없는 원댓글 삭제 시 하드 삭제된다")
    void t19() {
        // given
        Long commentId = 10L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", userId);
        Comment comment = new Comment(post, author, "원댓글 내용");
        ReflectionTestUtils.setField(comment, "id", commentId);
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));
        given(commentRepository.existsByParent_Id(commentId)).willReturn(false);

        // when
        commentService.deleteComment(commentId, userId);

        // then
        assertThat(comment.isDeleted()).isFalse();
        assertThat(comment.getContent()).isEqualTo("원댓글 내용");
        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("t20: 답글 삭제는 하위 답글 존재 여부와 무관하게 항상 하드 삭제된다")
    void t20() {
        // given
        Long replyId = 20L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(author, "id", userId);
        Comment original = new Comment(post, author, "원댓글");
        Comment reply = new Comment(post, author, "답글 내용", original);
        ReflectionTestUtils.setField(reply, "id", replyId);
        given(commentRepository.findById(replyId)).willReturn(Optional.of(reply));

        // when
        commentService.deleteComment(replyId, userId);

        // then
        assertThat(reply.isDeleted()).isFalse();
        verify(commentRepository).delete(reply);
        verify(commentRepository, never()).existsByParent_Id(any());
    }

    @Test
    @DisplayName("t21: 목록 조회 시 원댓글에 답글이 함께 매핑되고, isUpvoted는 답글에도 적용되며 소프트 삭제된 원댓글도 답글은 노출된다")
    void t21() {
        // given
        Long postId = 1L;
        Long userId = 7L;
        User parentAuthor = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(parentAuthor, "id", 42L);
        Course course = new Course("서울숲 코스", "11200", 2500);
        Post post = new Post(parentAuthor, course, "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
        Comment parent = new Comment(post, parentAuthor, "원댓글 내용");
        ReflectionTestUtils.setField(parent, "id", 10L);
        parent.softDelete();
        User replier = User.createLocal("reply@test.com", "dummy-hash", "답글러");
        ReflectionTestUtils.setField(replier, "id", 8L);
        Comment reply = new Comment(post, replier, "답글 내용", parent);
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(parent)));
        given(commentRepository.findByParent_IdInOrderByCreatedAtAsc(List.of(10L)))
                .willReturn(List.of(reply));
        given(commentUpvoteRepository.findUpvotedCommentIds(userId, List.of(10L, 11L)))
                .willReturn(List.of(11L));

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, userId, "latest", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
        CommentService.CommentResponse parentItem = response.content().get(0);
        assertThat(parentItem.commentId()).isEqualTo(10L);
        assertThat(parentItem.isDeleted()).isTrue();
        assertThat(parentItem.content()).isEqualTo("삭제된 댓글입니다.");
        assertThat(parentItem.isUpvoted()).isFalse();
        assertThat(parentItem.replies()).hasSize(1);
        CommentService.CommentResponse replyItem = parentItem.replies().get(0);
        assertThat(replyItem.commentId()).isEqualTo(11L);
        assertThat(replyItem.content()).isEqualTo("답글 내용");
        assertThat(replyItem.isUpvoted()).isTrue();
    }

    @Test
    @DisplayName("t22: sort=upvote면 공감순 조회 메서드를 호출하고, 답글은 기존 createdAt ASC 조회를 그대로 사용한다")
    void t22() {
        // given
        Long postId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment top = new Comment(post, author, "공감 많은 댓글");
        ReflectionTestUtils.setField(top, "id", 10L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByUpvoteCountDescCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(top)));
        given(commentRepository.findByParent_IdInOrderByCreatedAtAsc(List.of(10L)))
                .willReturn(List.of());

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, null, "upvote", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
        verify(commentRepository).findByPost_IdAndParentIsNullOrderByUpvoteCountDescCreatedAtDesc(eq(postId), any(Pageable.class));
        verify(commentRepository, never()).findByPost_IdAndParentIsNullOrderByCreatedAtDesc(any(), any());
        verify(commentRepository).findByParent_IdInOrderByCreatedAtAsc(List.of(10L));
    }

    @Test
    @DisplayName("t23: 알 수 없는 sort 값이면 예외 없이 최신순(createdAt DESC) 조회로 폴백한다")
    void t23() {
        // given
        Long postId = 1L;
        Post post = newPost();
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, null, "boom", PageRequest.of(0, 20));

        // then
        assertThat(response.content()).isEmpty();
        verify(commentRepository).findByPost_IdAndParentIsNullOrderByCreatedAtDesc(eq(postId), any(Pageable.class));
        verify(commentRepository, never()).findByPost_IdAndParentIsNullOrderByUpvoteCountDescCreatedAtDesc(any(), any());
    }
}
