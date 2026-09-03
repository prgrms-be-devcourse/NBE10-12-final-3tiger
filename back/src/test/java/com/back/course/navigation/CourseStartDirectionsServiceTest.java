package com.back.course.navigation;

import com.back.course.navigation.dto.DirectionsMode;
import com.back.course.navigation.dto.DirectionsStatus;
import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.course.navigation.service.CourseStartDirectionsService;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.map.kakao.KakaoDirectionsClient;
import com.back.map.kakao.dto.KakaoRouteDirectionsResponse;
import com.back.map.kakao.dto.KakaoTransitDirectionsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CourseStartDirectionsServiceTest {

    private final CourseNavigationRepository repository = mock(CourseNavigationRepository.class);
    private final KakaoDirectionsClient directionsClient = mock(KakaoDirectionsClient.class);
    private final CourseNavigationView startPoint = mock(CourseNavigationView.class);

    private CourseStartDirectionsService service;

    @BeforeEach
    void setUp() {
        service = new CourseStartDirectionsService(repository, directionsClient);
        given(startPoint.getCourseId()).willReturn(15L);
        given(startPoint.getName()).willReturn("서울식물원 코스");
        given(startPoint.getStartLat()).willReturn(37.5690);
        given(startPoint.getStartLng()).willReturn(126.8350);
        given(repository.findNavigationByCourseId(15L)).willReturn(Optional.of(startPoint));
    }

    @Test
    void walkModeCallsOnlyWalkAndMapsRoute() {
        given(directionsClient.getWalk(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(route("OK", 1200, 900, "https://map.kakao.com/link/by/walk/test"));

        var result = service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.WALK);

        assertThat(result.mode()).isEqualTo(DirectionsMode.WALK);
        assertThat(result.status()).isEqualTo(DirectionsStatus.ROUTE_AVAILABLE);
        assertThat(result.startable()).isFalse();
        assertThat(result.routes()).hasSize(1);
        assertThat(result.routes().getFirst().type()).isEqualTo("WALK");
        assertThat(result.routes().getFirst().distanceMeters()).isEqualTo(1200);
        assertThat(result.routes().getFirst().estimatedSeconds()).isEqualTo(900);
        assertThat(result.routes().getFirst().segments()).hasSize(1);
        assertThat(result.routes().getFirst().segments().getFirst().path().coordinates())
                .containsExactly(
                        List.of(126.80, 37.50),
                        List.of(126.835, 37.569)
                );
        verify(directionsClient).getWalk(
                37.50, 126.80, 37.5690, 126.8350,
                "서울식물원 코스 출발점"
        );
    }

    @Test
    void bicycleModeUsesBicycleRoute() {
        given(directionsClient.getBicycle(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(route("OK", 1400, 420, "https://map.kakao.com/link/by/bicycle/test"));

        var result = service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.BICYCLE);

        assertThat(result.mode()).isEqualTo(DirectionsMode.BICYCLE);
        assertThat(result.routes().getFirst().type()).isEqualTo("BICYCLE");
        assertThat(result.routes().getFirst().distanceMeters()).isEqualTo(1400);
        assertThat(result.routes().getFirst().segments().getFirst().mode())
                .isEqualTo("BICYCLE");
        verify(directionsClient).getBicycle(
                37.50, 126.80, 37.5690, 126.8350,
                "서울식물원 코스 출발점"
        );
    }

    @Test
    void publicTransitMapsAllCandidatesAndRepresentative() {
        var first = new KakaoTransitDirectionsResponse.Route(
                new KakaoTransitDirectionsResponse.RouteProperties(
                        "BUS", 5000, 2100, 0,
                        new KakaoTransitDirectionsResponse.Fare(1450, null, null)
                ),
                List.of(transitStep(
                        "BUS", "6645번 버스 승차", 4100, 1500,
                        List.of("강서구청", "화곡역", "서울식물원"),
                        List.of(new KakaoTransitDirectionsResponse.Vehicle("마을", "6645"))
                ))
        );
        var second = new KakaoTransitDirectionsResponse.Route(
                new KakaoTransitDirectionsResponse.RouteProperties(
                        "BUS_AND_SUBWAY", 5500, 1800, 1,
                        new KakaoTransitDirectionsResponse.Fare(1550, null, null)
                ),
                List.of(transitStep(
                        "SUBWAY", "5호선 승차", 4200, 1200,
                        List.of("까치산역", "화곡역", "마곡역"),
                        List.of(new KakaoTransitDirectionsResponse.Vehicle("SUBWAY", "5호선"))
                ))
        );
        given(directionsClient.getPublicTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(new KakaoTransitDirectionsResponse(
                        "OK",
                        new KakaoTransitDirectionsResponse.Properties(
                                2, 1, 0, 1,
                                "https://map.kakao.com/link/by/traffic/test"
                        ),
                        List.of(first, second)
                ));

        var result = service.getDirectionsToStart(
                15L, 37.50, 126.80, DirectionsMode.PUBLIC_TRANSIT
        );

        assertThat(result.routes()).hasSize(2);
        assertThat(result.routes().get(0).routeIndex()).isZero();
        assertThat(result.routes().get(0).distanceMeters()).isEqualTo(5000);
        assertThat(result.routes().get(1).routeIndex()).isEqualTo(1);
        assertThat(result.routes().get(1).transfers()).isEqualTo(1);
        assertThat(result.routes().get(1).fareWon()).isEqualTo(1550);

        var busSegment = result.routes().getFirst().segments().getFirst();
        assertThat(busSegment.segmentIndex()).isZero();
        assertThat(busSegment.mode()).isEqualTo("BUS");
        assertThat(busSegment.vehicleNames()).containsExactly("6645");
        assertThat(busSegment.stops())
                .extracting(stop -> stop.role())
                .containsExactly("BOARDING", "PASSING", "ALIGHTING");
        assertThat(busSegment.path().coordinates())
                .containsExactly(
                        List.of(126.8495, 37.5509),
                        List.of(126.827658, 37.5667106)
                );
    }

    @Test
    void nearStartSkipsKakaoApi() {
        var result = service.getDirectionsToStart(
                15L, 37.5690, 126.8350, DirectionsMode.WALK
        );

        assertThat(result.status()).isEqualTo(DirectionsStatus.ALREADY_NEAR_START);
        assertThat(result.startable()).isTrue();
        assertThat(result.routes()).isEmpty();
        assertThat(result.landingUrl()).isNull();
        verifyNoInteractions(directionsClient);
    }

    @Test
    void missingCourseReturnsCourseNotFound() {
        given(repository.findNavigationByCourseId(99L)).willReturn(Optional.empty());

        assertError(
                () -> service.getDirectionsToStart(99L, 37.50, 126.80, DirectionsMode.WALK),
                ErrorCode.COURSE_NOT_FOUND
        );
    }

    @Test
    void invalidCoordinateIsRejectedBeforeRepositoryCall() {
        assertError(
                () -> service.getDirectionsToStart(15L, 91, 126.80, DirectionsMode.WALK),
                ErrorCode.INVALID_COORDINATE
        );
        verifyNoInteractions(repository);
    }

    @Test
    void missingStartPointReturnsUnprocessableEntity() {
        given(startPoint.getStartLat()).willReturn(null);

        assertError(
                () -> service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.WALK),
                ErrorCode.COURSE_START_POINT_NOT_FOUND
        );
    }

    @Test
    void routeNotFoundUsesModeSpecificError() {
        given(directionsClient.getBicycle(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(new KakaoRouteDirectionsResponse("ROUTE_RESULT_NOT_FOUND", null));

        assertError(
                () -> service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.BICYCLE),
                ErrorCode.KAKAO_BICYCLE_ROUTE_NOT_FOUND
        );
    }

    @Test
    void untrustedLandingUrlIsRejected() {
        given(directionsClient.getWalk(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(route("OK", 1200, 900, "https://example.com/redirect"));

        assertError(
                () -> service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.WALK),
                ErrorCode.KAKAO_DIRECTIONS_FAILED
        );
    }

    @Test
    void missingSegmentPathIsRejectedInsteadOfReturningEmptyCoordinates() {
        var step = new KakaoRouteDirectionsResponse.Step(
                new KakaoRouteDirectionsResponse.StepProperties(
                        100, "직진", 80, 126.80, 37.50
                ),
                new KakaoRouteDirectionsResponse.Path(List.of())
        );
        var response = new KakaoRouteDirectionsResponse(
                "OK",
                new KakaoRouteDirectionsResponse.Route(
                        new KakaoRouteDirectionsResponse.RouteProperties(
                                100, 80, "https://map.kakao.com/link/by/walk/test"
                        ),
                        List.of(new KakaoRouteDirectionsResponse.Leg(
                                new KakaoRouteDirectionsResponse.LegProperties(100, 80),
                                List.of(step)
                        ))
                )
        );
        given(directionsClient.getWalk(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .willReturn(response);

        assertError(
                () -> service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.WALK),
                ErrorCode.KAKAO_DIRECTIONS_FAILED
        );
    }

    private KakaoRouteDirectionsResponse route(
            String status, int distance, int seconds, String landingUrl
    ) {
        return new KakaoRouteDirectionsResponse(
                status,
                new KakaoRouteDirectionsResponse.Route(
                        new KakaoRouteDirectionsResponse.RouteProperties(
                                distance, seconds, landingUrl
                        ),
                        List.of(new KakaoRouteDirectionsResponse.Leg(
                                new KakaoRouteDirectionsResponse.LegProperties(distance, seconds),
                                List.of(new KakaoRouteDirectionsResponse.Step(
                                        new KakaoRouteDirectionsResponse.StepProperties(
                                                distance, "출발점까지 이동", seconds,
                                                126.80, 37.50
                                        ),
                                        new KakaoRouteDirectionsResponse.Path(List.of(
                                                List.of(126.80, 37.50),
                                                List.of(126.835, 37.569)
                                        ))
                                ))
                        ))
                )
        );
    }

    private KakaoTransitDirectionsResponse.Step transitStep(
            String type,
            String guidance,
            int distance,
            int seconds,
            List<String> stopNames,
            List<KakaoTransitDirectionsResponse.Vehicle> vehicles
    ) {
        return new KakaoTransitDirectionsResponse.Step(
                new KakaoTransitDirectionsResponse.StepProperties(
                        guidance,
                        type,
                        distance,
                        seconds,
                        stopNames.stream()
                                .map(KakaoTransitDirectionsResponse.Stop::new)
                                .toList(),
                        vehicles
                ),
                new KakaoTransitDirectionsResponse.Path(List.of(
                        List.of(126.8495, 37.5509),
                        List.of(126.827658, 37.5667106)
                ))
        );
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
