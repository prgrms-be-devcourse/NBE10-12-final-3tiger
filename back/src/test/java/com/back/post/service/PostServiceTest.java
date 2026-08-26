package com.back.post.service;

import com.back.comment.repository.CommentRepository;
import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostLikeRepository;
import com.back.post.repository.PostRepository;
import com.back.post.storage.PhotoStorage;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository posts;
    @Mock UserRepository users;
    @Mock CourseRepository courses;
    @Mock PostLikeRepository postLikes;
    @Mock CommentRepository comments;
    @Mock PhotoStorage storage;
    @InjectMocks PostService postService;

    private User user(Long id) {
        User user = User.createLocal("test@example.com", "hash", "산책러");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Course course(Long id) {
        Course course = new Course("POST 테스트 코스", "11500", 2500);
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    private Post post(Long id, User user, Course course) {
        Post post = new Post(user, course, "서울숲 기록", "좋은 산책이었습니다.",
                "https://example.com/walk.jpg", LocalDateTime.of(2026, 8, 26, 9, 0));
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("게시물 작성 시 인증 사용자와 코스를 연결하고 ID를 반환한다")
    void create() {
        User user = user(1L);
        Course course = course(1L);
        Post saved = post(10L, user, course);
        given(users.findById(1L)).willReturn(Optional.of(user));
        given(courses.findById(1L)).willReturn(Optional.of(course));
        given(posts.save(any(Post.class))).willReturn(saved);

        var command = new PostService.CreateCommand(1L, "서울숲 기록", "좋은 산책이었습니다.",
                "https://example.com/walk.jpg", LocalDateTime.of(2026, 8, 26, 9, 0));
        PostService.CreatedPost result = postService.create(1L, command);

        assertThat(result.postId()).isEqualTo(10L);
        verify(posts).save(any(Post.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 작성하면 401을 반환한다")
    void createRejectsUnknownUser() {
        given(users.findById(99L)).willReturn(Optional.empty());
        var command = new PostService.CreateCommand(1L, "제목", "내용", null, LocalDateTime.now());

        ApiException exception = catchThrowableOfType(() -> postService.create(99L, command), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(courses, never()).findById(any());
        verify(posts, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 코스로 작성하면 404를 반환한다")
    void createRejectsUnknownCourse() {
        given(users.findById(1L)).willReturn(Optional.of(user(1L)));
        given(courses.findById(999L)).willReturn(Optional.empty());
        var command = new PostService.CreateCommand(999L, "제목", "내용", null, LocalDateTime.now());

        ApiException exception = catchThrowableOfType(() -> postService.create(1L, command), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 코스입니다.");
        verify(posts, never()).save(any());
    }

    @Test
    @DisplayName("피드에서 제목·내용·좋아요 수·댓글 수·현재 사용자 좋아요 여부를 매핑한다")
    void feed() {
        Post post = post(10L, user(2L), course(1L));
        post.increaseLikeCount();
        given(posts.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of(post)));
        given(postLikes.existsByPost_IdAndUser_Id(10L, 1L)).willReturn(true);
        given(comments.countByPost_Id(10L)).willReturn(3L);

        PageResponse<PostService.FeedItem> result = postService.feed(1L, null, "latest", 0, 20);

        PostService.FeedItem item = result.content().getFirst();
        assertThat(item.title()).isEqualTo("서울숲 기록");
        assertThat(item.content()).isEqualTo("좋은 산책이었습니다.");
        assertThat(item.likeCount()).isEqualTo(1);
        assertThat(item.commentCount()).isEqualTo(3);
        assertThat(item.isLiked()).isTrue();
    }

    @Test
    @DisplayName("비로그인 피드에서는 좋아요 조회 없이 isLiked=false를 반환한다")
    void anonymousFeed() {
        Post post = post(10L, user(2L), course(1L));
        given(posts.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of(post)));

        PostService.FeedItem item = postService.feed(null, null, "latest", 0, 20).content().getFirst();

        assertThat(item.isLiked()).isFalse();
        verify(postLikes, never()).existsByPost_IdAndUser_Id(any(), any());
    }

    @Test
    @DisplayName("작성자 본인은 게시물을 삭제할 수 있다")
    void deleteOwnPost() {
        Post post = post(10L, user(1L), course(1L));
        given(posts.findById(10L)).willReturn(Optional.of(post));

        postService.delete(1L, 10L);

        verify(posts).delete(post);
    }

    @Test
    @DisplayName("다른 사용자의 게시물은 삭제할 수 없다")
    void cannotDeleteOthersPost() {
        Post post = post(10L, user(2L), course(1L));
        given(posts.findById(10L)).willReturn(Optional.of(post));

        ApiException exception = catchThrowableOfType(() -> postService.delete(1L, 10L), ApiException.class);

        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(posts, never()).delete(any());
    }
}
