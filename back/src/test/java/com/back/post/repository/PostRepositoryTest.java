package com.back.post.repository;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.config.JpaConfig;
import com.back.post.domain.Post;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:post;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class PostRepositoryTest {

    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    @Test
    @DisplayName("제목만 대소문자 없이 부분 검색하고 본문만 일치하는 게시물은 제외한다")
    void searchesOnlyTitleIgnoringCase() {
        User user = users.save(User.createLocal("post-search@example.com", "hash", "산책러"));
        Course matchingCourse = courses.save(new Course("SeOuL Forest Walk", "11500", 2000));
        Course contentOnlyCourse = courses.save(new Course("양천 산책길", "11500", 1500));
        posts.save(new Post(user, matchingCourse, "좋은 산책", null, LocalDateTime.now()));
        posts.save(new Post(user, contentOnlyCourse, "SEOUL FOREST 후기", null, LocalDateTime.now()));

        Page<Post> result = posts.findByTitleContainingIgnoreCase(
                "seoul forest", PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getTitle()).isEqualTo("SeOuL Forest Walk");
    }

    @Test
    @DisplayName("제목 검색 결과에 좋아요순 정렬과 페이징을 적용한다")
    void appliesPopularitySortAndPagingToTitleSearch() {
        User user = users.save(User.createLocal("post-page@example.com", "hash", "산책러"));
        Course firstCourse = courses.save(new Course("서울숲 A", "11500", 1000));
        Course secondCourse = courses.save(new Course("서울숲 B", "11500", 1000));
        Post lessPopular = posts.save(new Post(user, firstCourse, "A", null, LocalDateTime.now()));
        Post morePopular = new Post(user, secondCourse, "B", null, LocalDateTime.now());
        morePopular.increaseLikeCount();
        morePopular.increaseLikeCount();
        posts.save(morePopular);

        Page<Post> result = posts.findByTitleContainingIgnoreCase(
                "서울숲", PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "likeCount", "createdAt", "id")));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(morePopular);
        assertThat(result.getContent()).doesNotContain(lessPopular);
    }
}
