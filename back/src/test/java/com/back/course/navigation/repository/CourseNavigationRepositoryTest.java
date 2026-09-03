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
    @DisplayName("PostGIS 경로에서 안내 좌표와 검증 정보를 조회한다")
    void findsNavigationProjection() {
        CourseNavigationView view = repository.findNavigationByCourseId(101L).orElseThrow();

        assertThat(view.getStartLat()).isEqualTo(37.544);
        assertThat(view.getStartLng()).isEqualTo(127.037);
        assertThat(view.getEndLat()).isEqualTo(37.544);
        assertThat(view.getEndLng()).isEqualTo(127.037);
        assertThat(view.getGeometryType()).isEqualToIgnoringCase("LINESTRING");
        assertThat(view.getSrid()).isEqualTo(4326);
        assertThat(view.getCoordinateCount()).isEqualTo(3);
        assertThat(view.getPathValid()).isTrue();
        assertThat(view.getPathEmpty()).isFalse();
        assertThat(view.getCalculatedDistanceM()).isPositive();
        assertThat(view.getStartEndDistanceM()).isLessThan(0.1);
        assertThat(view.getPathGeoJson()).contains("LineString", "127.037", "37.544");
    }

    @Test
    @DisplayName("start_point가 없으면 경로의 첫 좌표를 출발점으로 사용한다")
    void fallsBackToPathStartPoint() {
        CourseNavigationView view = repository.findNavigationByCourseId(102L).orElseThrow();

        assertThat(view.getStartLat()).isEqualTo(37.55);
        assertThat(view.getStartLng()).isEqualTo(126.85);
        assertThat(view.getEndLat()).isEqualTo(37.551);
        assertThat(view.getEndLng()).isEqualTo(126.851);
    }
}
