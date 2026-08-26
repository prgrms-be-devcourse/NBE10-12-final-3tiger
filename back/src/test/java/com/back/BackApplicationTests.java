package com.back;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.back.TestAuthentication.authenticatedAs;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
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
        String body = "{\"courseId\":" + courseId + ",\"title\":\"산책 기록\",\"content\":\"오늘 산책\",\"photoUrl\":\"https://cdn.example/walk.jpg\",\"walkedAt\":\"2026-08-19T17:30:00\"}";
        String response = mvc.perform(post("/api/v1/posts").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.postId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long postId = Long.parseLong(response.replaceAll(".*\\\"postId\\\":(\\d+).*", "$1"));
        mvc.perform(get("/api/v1/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].photoUrl").value("https://cdn.example/walk.jpg"));
        mvc.perform(delete("/api/v1/posts/{id}", postId).with(authenticatedAs(userId))).andExpect(status().isOk());
    }

    @Test void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/posts/me"))
                .andExpect(status().isUnauthorized());
    }
}
