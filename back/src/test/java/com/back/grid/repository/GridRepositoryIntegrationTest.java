package com.back.grid.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
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

@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "app.auth.allow-dev-user=false"
})
@ActiveProfiles("test")
@Sql("/grid/grid-score-test-data.sql")
class GridRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    ).withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    private final GridRepository gridRepository;

    @Autowired
    GridRepositoryIntegrationTest(GridRepository gridRepository) {
        this.gridRepository = gridRepository;
    }

    @Test
    void findsOnlyGridsWhoseCentroidIsInsideOrOnBboxBoundary() {
        List<GridOverlayProjection> grids = gridRepository.findAllByCentroidIn(
                126.800,
                37.500,
                126.810,
                37.510
        );

        assertThat(grids).hasSize(2);
        assertThat(grids).extracting(GridOverlayProjection::getGridId)
                .containsExactly(1L, 2L);

        GridOverlayProjection first = grids.getFirst();
        assertThat(first.getRegionCode()).isEqualTo("11500");
        assertThat(first.getCentroidLng()).isEqualTo(126.805);
        assertThat(first.getCentroidLat()).isEqualTo(37.505);
        assertThat(first.getFlatness()).isEqualByComparingTo(new BigDecimal("0.100"));
        assertThat(first.getShadeSummer()).isEqualByComparingTo(new BigDecimal("0.200"));
        assertThat(first.getShadeWinterSun()).isEqualByComparingTo(new BigDecimal("0.300"));
        assertThat(first.getTrafficLow()).isEqualByComparingTo(new BigDecimal("0.400"));
        assertThat(first.getWheelchair()).isEqualByComparingTo(new BigDecimal("0.500"));
        assertThat(first.getSurfaceNatural()).isEqualByComparingTo(new BigDecimal("0.600"));
        assertThat(first.getBenchDensity()).isEqualByComparingTo(new BigDecimal("0.700"));
        assertThat(first.getRestroomProximity()).isEqualByComparingTo(new BigDecimal("0.800"));
        assertThat(first.getWaterFacility()).isEqualByComparingTo(new BigDecimal("0.900"));
    }
}
