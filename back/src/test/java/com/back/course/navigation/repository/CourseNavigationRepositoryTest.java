package com.back.course.navigation.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@Sql("/navigation/course-navigation-fixture.sql")
class CourseNavigationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    ).withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    private final CourseNavigationRepository repository;

    @Autowired
    CourseNavigationRepositoryTest(CourseNavigationRepository repository) {
        this.repository = repository;
    }

    @Test
    @DisplayName("저장된 경로 정보로 안내 응답 projection을 조회한다")
    void findsNavigationProjection() {
        CourseNavigationView view = repository.findNavigationByCourseId(101L).orElseThrow();

        assertThat(view.getStartLat()).isEqualTo(37.544);
        assertThat(view.getStartLng()).isEqualTo(127.037);
        assertThat(view.getEndLat()).isEqualTo(37.544);
        assertThat(view.getEndLng()).isEqualTo(127.037);
        assertThat(view.getPathGeoJson()).contains("LineString", "127.037", "37.544");
    }

    @Test
    @DisplayName("저장 시 계산된 시작점과 종료점을 조회한다")
    void findsStoredStartAndEndPoints() {
        CourseNavigationView view = repository.findNavigationByCourseId(102L).orElseThrow();

        assertThat(view.getStartLat()).isEqualTo(37.55);
        assertThat(view.getStartLng()).isEqualTo(126.85);
        assertThat(view.getEndLat()).isEqualTo(37.551);
        assertThat(view.getEndLng()).isEqualTo(126.851);
    }

    @Test
    @DisplayName("길찾기용 출발점은 필요한 정보만 조회한다")
    void findsStartPointProjection() {
        CourseStartPointView view = repository.findStartPointByCourseId(101L).orElseThrow();

        assertThat(view.getCourseId()).isEqualTo(101L);
        assertThat(view.getName()).isNotBlank();
        assertThat(view.getStartLat()).isEqualTo(37.544);
        assertThat(view.getStartLng()).isEqualTo(127.037);
    }

    @Test
    @DisplayName("길찾기 projection도 저장된 출발점을 조회한다")
    void startPointProjectionUsesStoredStartPoint() {
        CourseStartPointView view = repository.findStartPointByCourseId(102L).orElseThrow();

        assertThat(view.getStartLat()).isEqualTo(37.55);
        assertThat(view.getStartLng()).isEqualTo(126.85);
    }
}
