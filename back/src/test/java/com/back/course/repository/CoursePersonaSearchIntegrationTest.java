package com.back.course.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:persona/course-persona-fixture.sql"
})
@ActiveProfiles("test")
class CoursePersonaSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    ).withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    private final CourseRepository courses;

    @Autowired
    CoursePersonaSearchIntegrationTest(CourseRepository courses) {
        this.courses = courses;
    }

    @Test void searchByRegion_orderByStrollerScore_promotesFlatPavedCourse() {
        List<CourseListView> rows = courses.searchByRegion(
                "11500", null, null, null,
                true, "score", "stroller",
                10, 0);

        assertThat(rows).extracting(CourseListView::getCourseId)
                .containsExactly(101L, 103L, 102L);
    }

    @Test void searchByRegion_orderByDogScore_promotesNaturalShadedCourse() {
        List<CourseListView> rows = courses.searchByRegion(
                "11500", null, null, null,
                true, "score", "dog",
                10, 0);

        assertThat(rows).extracting(CourseListView::getCourseId)
                .containsExactly(102L, 103L, 101L);
    }

    @Test void searchByRegion_nullPersona_fallsBackToUniformAverage() {
        List<CourseListView> rows = courses.searchByRegion(
                "11500", null, null, null,
                true, "score", null,
                10, 0);

        // 균등 평균 기준: 101(0.585), 102(0.555), 103(0.520)
        assertThat(rows).extracting(CourseListView::getCourseId)
                .containsExactly(101L, 102L, 103L);
    }

    @Test void searchByLocation_orderByDogScore_promotesNaturalShadedCourse() {
        List<CourseListView> rows = courses.searchByLocation(
                37.550, 126.850, 5000,
                null, null, null,
                true, "score", "dog",
                10, 0);

        assertThat(rows).extracting(CourseListView::getCourseId)
                .containsExactly(102L, 103L, 101L);
    }
}
