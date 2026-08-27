package com.back.post.service;

import com.back.course.domain.Course;
import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.domain.PostLike;
import com.back.post.repository.PostLikeRepository;
import com.back.post.repository.PostRepository;
import com.back.comment.repository.CommentRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostLikeWriter postLikeWriter;

    @InjectMocks
    private PostLikeService postLikeService;

    private Post newPost() {
        User user = User.createLocal("test@test.com", "dummy-hash", "산책러");
        Course course = new Course("서울숲 코스", "11200", 2500);
        return new Post(user, course, "산책 기록", "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
    }

    @Test
    @DisplayName("t1: 좋아요 등록 성공 시 likeCount가 1 증가하고 isLiked=true를 반환한다")
    void t1() {
        // given
        Post post = newPost();
        Long postId = 1L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(User.createLocal("test@test.com", "dummy-hash", "산책러")));

        // when
        PostLikeService.LikeResult result = postLikeService.like(postId, userId);

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1);
        verify(postLikeWriter).trySaveLike(eq(post), any());
        verify(postRepository).increaseLikeCount(postId);
    }

    @Test
    @DisplayName("t2: 이미 좋아요한 상태에서 다시 요청하면 likeCount는 그대로고 isLiked=true만 반환한다")
    void t2() {
        // given
        Post post = newPost();
        for (int i = 0; i < 5; i++) post.increaseLikeCount();
        Long postId = 1L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)).willReturn(true);

        // when
        PostLikeService.LikeResult result = postLikeService.like(postId, userId);

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(5);
        verify(postLikeWriter, never()).trySaveLike(any(), any());
        verify(userRepository, never()).findById(any());
        verify(postRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("t2b: 동시 요청으로 유니크 제약에 걸리면(DataIntegrityViolationException) 이미 좋아요 상태로 간주하고 likeCount를 그대로 반환한다")
    void t2b() {
        // given
        Post post = newPost();
        for (int i = 0; i < 5; i++) post.increaseLikeCount();
        Long postId = 1L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(User.createLocal("test@test.com", "dummy-hash", "산책러")));
        willThrow(new DataIntegrityViolationException("duplicate key")).given(postLikeWriter).trySaveLike(any(), any());

        // when
        PostLikeService.LikeResult result = postLikeService.like(postId, userId);

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(5);
        verify(postRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("t3: 존재하지 않는 게시물에 좋아요 요청 시 404 ApiException이 발생한다")
    void t3() {
        // given
        Long postId = 999L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> postLikeService.like(postId, userId), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시물입니다.");
    }

    @Test
    @DisplayName("t4: 좋아요 취소 성공 시 likeCount가 1 감소하고 isLiked=false를 반환한다")
    void t4() {
        // given
        Post post = newPost();
        for (int i = 0; i < 3; i++) post.increaseLikeCount();
        Long postId = 1L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.deleteByPost_IdAndUser_Id(postId, userId)).willReturn(1);

        // when
        PostLikeService.LikeResult result = postLikeService.unlike(postId, userId);

        // then
        assertThat(result.isLiked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(2);
        verify(postRepository).decreaseLikeCount(postId);
    }

    @Test
    @DisplayName("t5: 좋아요하지 않은 상태에서 취소 요청 시 likeCount는 그대로고 isLiked=false만 반환한다")
    void t5() {
        // given
        Post post = newPost();
        Long postId = 1L;
        Long userId = 1L;
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.deleteByPost_IdAndUser_Id(postId, userId)).willReturn(0);

        // when
        PostLikeService.LikeResult result = postLikeService.unlike(postId, userId);

        // then
        assertThat(result.isLiked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0);
        verify(postRepository, never()).decreaseLikeCount(any());
    }

    @Test
    @DisplayName("t6: 내 좋아요 목록 조회 시 PageResponse로 필드가 정상 매핑된다")
    void t6() {
        // given
        Long userId = 1L;
        User user = User.createLocal("test@test.com", "dummy-hash", "산책러");
        Course course = new Course("서울숲 코스", "11200", 2500);
        ReflectionTestUtils.setField(course, "id", 20L);
        Post post = new Post(user, course, "산책 기록", "오늘도 산책", "http://example.com/photo.jpg", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "id", 10L);
        PostLike postLike = new PostLike(post, user);

        given(postLikeRepository.findByUser_IdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(postLike)));

        // when
        PageResponse<PostLikeService.LikedPostItem> response = postLikeService.myLikes(userId, 0, 20);

        // then
        assertThat(response.content()).hasSize(1);
        PostLikeService.LikedPostItem item = response.content().get(0);
        assertThat(item.postId()).isEqualTo(10L);
        assertThat(item.courseId()).isEqualTo(20L);
        assertThat(item.nickname()).isEqualTo("산책러");
        assertThat(item.title()).isEqualTo("산책 기록");
        assertThat(item.content()).isEqualTo("오늘도 산책");
        assertThat(item.photoUrl()).isEqualTo("http://example.com/photo.jpg");
        assertThat(item.likeCount()).isEqualTo(0);
        assertThat(item.likedAt()).isEqualTo(postLike.getCreatedAt());
    }
}
