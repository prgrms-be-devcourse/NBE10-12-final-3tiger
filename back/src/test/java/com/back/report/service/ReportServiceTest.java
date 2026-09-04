package com.back.report.service;

import com.back.comment.domain.Comment;
import com.back.comment.repository.CommentRepository;
import com.back.course.domain.Course;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.report.domain.Report;
import com.back.report.domain.ReportReason;
import com.back.report.domain.ReportTargetType;
import com.back.report.repository.ReportRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final int THRESHOLD = 3;
    private static final long REPORTER_ID = 1L;
    private static final long OTHER_USER_ID = 42L;

    @Mock private ReportRepository reports;
    @Mock private UserRepository users;
    @Mock private PostRepository posts;
    @Mock private CommentRepository comments;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, users, posts, comments, THRESHOLD);
    }

    private User user(long id) {
        User user = User.createLocal("author@test.com", "dummy-hash", "글쓴이");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post post(long authorId) {
        return new Post(user(authorId), new Course("서울숲 코스", "11200", 2500), "오늘도 산책",
                "http://example.com/photo.jpg", LocalDateTime.now());
    }

    private Comment comment(long authorId) {
        return new Comment(post(authorId), user(authorId), "좋은 코스네요");
    }

    @Test
    @DisplayName("t1: 게시물 신고 성공 시 report를 저장하고 현재 신고 수와 hidden=false를 반환한다")
    void t1() {
        // given
        given(posts.findById(10L)).willReturn(Optional.of(post(OTHER_USER_ID)));
        given(reports.existsByReporter_IdAndTargetTypeAndTargetId(REPORTER_ID, ReportTargetType.POST, 10L)).willReturn(false);
        given(reports.countByTargetTypeAndTargetId(ReportTargetType.POST, 10L)).willReturn(1L);

        // when
        ReportService.ReportResult result = service.report(REPORTER_ID, ReportTargetType.POST, 10L, ReportReason.SPAM);

        // then
        assertThat(result.reportCount()).isEqualTo(1L);
        assertThat(result.hidden()).isFalse();
        verify(reports).save(any(Report.class));
        verify(posts, never()).hide(any());
    }

    @Test
    @DisplayName("t2: 이미 신고한 대상을 다시 신고하면 저장 없이 현재 상태를 그대로 반환한다(멱등)")
    void t2() {
        // given
        given(posts.findById(10L)).willReturn(Optional.of(post(OTHER_USER_ID)));
        given(reports.existsByReporter_IdAndTargetTypeAndTargetId(REPORTER_ID, ReportTargetType.POST, 10L)).willReturn(true);
        given(reports.countByTargetTypeAndTargetId(ReportTargetType.POST, 10L)).willReturn(2L);

        // when
        ReportService.ReportResult result = service.report(REPORTER_ID, ReportTargetType.POST, 10L, ReportReason.ABUSE);

        // then
        assertThat(result.reportCount()).isEqualTo(2L);
        assertThat(result.hidden()).isFalse();
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("t3: 게시물 신고 수가 임계치에 도달하면 자동으로 hidden 처리하고 hidden=true를 반환한다")
    void t3() {
        // given
        given(posts.findById(10L)).willReturn(Optional.of(post(OTHER_USER_ID)));
        given(reports.existsByReporter_IdAndTargetTypeAndTargetId(REPORTER_ID, ReportTargetType.POST, 10L)).willReturn(false);
        given(reports.countByTargetTypeAndTargetId(ReportTargetType.POST, 10L)).willReturn((long) THRESHOLD);

        // when
        ReportService.ReportResult result = service.report(REPORTER_ID, ReportTargetType.POST, 10L, ReportReason.SPAM);

        // then
        assertThat(result.hidden()).isTrue();
        verify(posts).hide(10L);
    }

    @Test
    @DisplayName("t4: 댓글 신고 수가 임계치에 도달하면 댓글을 hidden 처리한다")
    void t4() {
        // given
        given(comments.findById(20L)).willReturn(Optional.of(comment(OTHER_USER_ID)));
        given(reports.existsByReporter_IdAndTargetTypeAndTargetId(REPORTER_ID, ReportTargetType.COMMENT, 20L)).willReturn(false);
        given(reports.countByTargetTypeAndTargetId(ReportTargetType.COMMENT, 20L)).willReturn(9L);

        // when
        ReportService.ReportResult result = service.report(REPORTER_ID, ReportTargetType.COMMENT, 20L, ReportReason.HARASSMENT);

        // then
        assertThat(result.hidden()).isTrue();
        verify(comments).hide(20L);
    }

    @Test
    @DisplayName("t5: 자기 자신을 신고하면 400 ApiException이 발생한다")
    void t5() {
        // when
        ApiException exception = catchThrowableOfType(
                () -> service.report(REPORTER_ID, ReportTargetType.USER, REPORTER_ID, ReportReason.ETC), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("자기 자신은 신고할 수 없습니다.");
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("t6: 존재하지 않는 게시물을 신고하면 404 ApiException이 발생한다")
    void t6() {
        // given
        given(posts.findById(999L)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(
                () -> service.report(REPORTER_ID, ReportTargetType.POST, 999L, ReportReason.SPAM), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시물입니다.");
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("t7: 사용자 신고는 임계치에 도달해도 스키마상 숨김 대상이 아니므로 hidden=false를 반환한다")
    void t7() {
        // given
        given(users.existsById(5L)).willReturn(true);
        given(reports.existsByReporter_IdAndTargetTypeAndTargetId(REPORTER_ID, ReportTargetType.USER, 5L)).willReturn(false);
        given(reports.countByTargetTypeAndTargetId(ReportTargetType.USER, 5L)).willReturn(50L);

        // when
        ReportService.ReportResult result = service.report(REPORTER_ID, ReportTargetType.USER, 5L, ReportReason.ABUSE);

        // then
        assertThat(result.hidden()).isFalse();
        verify(posts, never()).hide(any());
        verify(comments, never()).hide(any());
    }

    @Test
    @DisplayName("t8: 본인 게시물을 신고하면 400 ApiException이 발생하고 저장하지 않는다")
    void t8() {
        // given
        given(posts.findById(10L)).willReturn(Optional.of(post(REPORTER_ID)));

        // when
        ApiException exception = catchThrowableOfType(
                () -> service.report(REPORTER_ID, ReportTargetType.POST, 10L, ReportReason.SPAM), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("본인 게시물은 신고할 수 없습니다.");
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("t9: 본인 댓글을 신고하면 400 ApiException이 발생하고 저장하지 않는다")
    void t9() {
        // given
        given(comments.findById(20L)).willReturn(Optional.of(comment(REPORTER_ID)));

        // when
        ApiException exception = catchThrowableOfType(
                () -> service.report(REPORTER_ID, ReportTargetType.COMMENT, 20L, ReportReason.ABUSE), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("본인 댓글은 신고할 수 없습니다.");
        verify(reports, never()).save(any());
    }
}
