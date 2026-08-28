package com.back.comment.service;

import com.back.comment.domain.Comment;
import com.back.comment.repository.CommentRepository;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.course.domain.Course;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("t1: 댓글 작성 성공 시 commentId를 반환한다")
    void t1() {
        // given
        Long postId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User user = User.createLocal("test@test.com", "dummy-hash", "산책러");
        Comment savedComment = new Comment(post, user, "좋은 코스네요");
        ReflectionTestUtils.setField(savedComment, "id", 100L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(commentRepository.save(any(Comment.class))).willReturn(savedComment);

        // when
        Long commentId = commentService.createComment(postId, userId, "좋은 코스네요");

        // then
        assertThat(commentId).isEqualTo(100L);
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
        Comment comment = new Comment(post, user, "좋은 코스네요");
        ReflectionTestUtils.setField(comment, "id", 10L);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(commentRepository.findByPost_IdOrderByCreatedAtDesc(eq(postId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(comment)));

        // when
        PageResponse<CommentService.CommentResponse> response = commentService.getComments(postId, PageRequest.of(0, 20));

        // then
        assertThat(response.content()).hasSize(1);
        CommentService.CommentResponse item = response.content().get(0);
        assertThat(item.commentId()).isEqualTo(10L);
        assertThat(item.userId()).isEqualTo(5L);
        assertThat(item.nickname()).isEqualTo("산책러");
        assertThat(item.content()).isEqualTo("좋은 코스네요");
        assertThat(item.upvoteCount()).isEqualTo(0);
        assertThat(item.createdAt()).isEqualTo(comment.getCreatedAt());
    }

    @Test
    @DisplayName("t4: 존재하지 않는 게시물의 댓글 목록 조회 시 404 ApiException이 발생한다")
    void t4() {
        // given
        Long postId = 999L;
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> commentService.getComments(postId, PageRequest.of(0, 20)), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시물입니다.");
    }

    @Test
    @DisplayName("t5: 댓글 공감 성공 시 upvoted=true이고 upvoteCount가 1 증가한다")
    void t5() {
        // given
        Long commentId = 1L;
        Long userId = 1L;
        Post post = newPost();
        User author = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        Comment comment = new Comment(post, author, "좋은 코스네요");
        User upvoter = User.createLocal("test@test.com", "dummy-hash", "산책러");
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
}
