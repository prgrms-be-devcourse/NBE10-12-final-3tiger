package com.back;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.comment.repository.CommentRepository;
import com.back.comment.repository.CommentUpvoteRepository;
import com.back.post.repository.PostLikeRepository;
import com.back.post.repository.PostRepository;
import com.back.post.domain.Post;
import com.back.post.service.PostLikeService;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.back.TestAuthentication.authenticatedAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired PostRepository posts;
    @Autowired PostLikeRepository postLikes;
    @Autowired CommentRepository comments;
    @Autowired CommentUpvoteRepository commentUpvotes;
    @Autowired PostLikeService postLikeService;
    Long userId; Long courseId;

    @BeforeEach
    void setUp() {
        userId = users.save(
                User.createLocal(
                        "test-" + UUID.randomUUID() + "@example.com",
                        "dummy-password-hash",
                        "산책러"
                )
        ).getId();

        courseId = courses.save(
                new Course("성수 서울숲 순환", "11200", 2500)
        ).getId();
    }

    @Test void bookmarkLifecycle() throws Exception {
        mvc.perform(put("/api/v1/courses/{id}/bookmarks", courseId).with(authenticatedAs(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isBookmarked").value(true));
        mvc.perform(get("/api/v1/users/me/bookmarks").with(authenticatedAs(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(delete("/api/v1/courses/{id}/bookmarks", courseId).with(authenticatedAs(userId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isBookmarked").value(false));
    }

    @Test void createReadAndDeletePost() throws Exception {
        String body = "{\"courseId\":" + courseId + ",\"content\":\"오늘 산책\",\"photoUrl\":\"https://cdn.example/walk.jpg\",\"walkedAt\":\"2026-08-19T17:30:00\"}";
        String response = mvc.perform(post("/api/v1/posts").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.postId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(response.replaceAll(".*\\\"postId\\\":(\\d+).*", "$1"));
        mvc.perform(put("/api/v1/posts/{id}/likes", postId).with(authenticatedAs(userId)))
                .andExpect(status().isOk());
        String commentResponse = mvc.perform(post("/api/v1/posts/{id}/comments", postId)
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"삭제될 댓글\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long commentId = Long.parseLong(commentResponse.replaceAll(".*\\\"data\\\":(\\d+).*", "$1"));
        mvc.perform(post("/api/v1/comments/{id}/upvote", commentId).with(authenticatedAs(userId)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].photoUrl").value("https://cdn.example/walk.jpg"));
        mvc.perform(delete("/api/v1/posts/{id}", postId).with(authenticatedAs(userId))).andExpect(status().isOk());

        assertFalse(posts.existsById(postId));
        assertFalse(postLikes.existsByPost_IdAndUser_Id(postId, userId));
        assertEquals(0, comments.countByPost_Id(postId));
        assertEquals(0, commentUpvotes.countByComment_Post_Id(postId));
    }

    @Test void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/posts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentLikesKeepAccurateCount() throws Exception {
        Long secondUserId = users.save(User.createLocal(
                "second-" + UUID.randomUUID() + "@example.com", "dummy-password-hash", "두번째 산책러"
        )).getId();
        Post post = posts.save(new Post(
                users.findById(userId).orElseThrow(), courses.findById(courseId).orElseThrow(),
                "동시에 좋아요", null, LocalDateTime.now()
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postLikeService.like(post.getId(), userId);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postLikeService.like(post.getId(), secondUserId);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals(2, posts.findById(post.getId()).orElseThrow().getLikeCount());
        assertTrue(postLikes.existsByPost_IdAndUser_Id(post.getId(), userId));
        assertTrue(postLikes.existsByPost_IdAndUser_Id(post.getId(), secondUserId));
    }

    @Test
    void concurrentSameUserLikesAreIdempotent() throws Exception {
        Post post = posts.save(new Post(
                users.findById(userId).orElseThrow(), courses.findById(courseId).orElseThrow(),
                "동시성 테스트", "같은 유저 동시 좋아요", null, LocalDateTime.now()
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postLikeService.like(post.getId(), userId);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return postLikeService.like(post.getId(), userId);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals(1, posts.findById(post.getId()).orElseThrow().getLikeCount());
        assertTrue(postLikes.existsByPost_IdAndUser_Id(post.getId(), userId));
    }
}
