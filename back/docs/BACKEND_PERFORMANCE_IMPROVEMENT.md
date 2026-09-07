# 백엔드 성능 개선 분석 및 적용 기록

## 1. 문서 목적

현재 백엔드에서 구현한 코스 내비게이션, 카카오 길찾기, 장소 검색 및 Redis Rate Limit 코드를 기준으로 대규모 트래픽에서 발생할 수 있는 병목을 분석하고 개선 우선순위를 정리한다.

포트폴리오에는 단순히 "성능을 개선했다"고 작성하기보다 다음 내용을 중심으로 활용할 수 있다.

- 어떤 병목 가능성을 발견했는가
- 왜 해당 항목을 먼저 해결했는가
- 코드 구조를 어떻게 변경했는가
- 변경으로 어떤 자원을 절약할 수 있는가
- 개선 효과를 어떤 지표로 검증할 것인가

실제 부하 테스트 전이므로 처리량이나 응답 시간에 대한 임의의 수치는 사용하지 않는다.

---

## 2. 개선 우선순위 요약

| 우선순위 | 개선 항목 | 상태 | 주요 목적 |
|---|---|---|---|
| 1 | DB 조회 트랜잭션과 카카오 외부 API 호출 분리 | 해결 | 외부 API 대기 중 DB 트랜잭션과 커넥션이 장시간 유지될 가능성 제거 |
| 1 | 길찾기용 경량 출발점 조회 쿼리 도입 | 해결 | 불필요한 PostGIS 연산과 대용량 경로 데이터 조회 제거 |
| 2 | 코스 내부 내비게이션 결과 캐싱 | 해결 | 변경 빈도가 낮은 코스 경로의 반복 DB 조회·검증·역직렬화 비용 감소 |
| 2 | 공간 데이터 검증과 거리 계산을 쓰기 시점으로 이동 | 미적용 | 조회할 때마다 반복되는 PostGIS 검증 비용 감소 |
| 2 | 길찾기 응답 HTTP 압축 | 미적용 | 전체 경로 좌표 전송량 감소 |
| 3 | 외부 HTTP 클라이언트 연결 풀 및 장애 격리 | 미적용 | 대규모 동시 요청 처리와 카카오 장애 전파 방지 |
| 3 | 장소 검색 요청 최적화 및 단기 캐싱 | 미적용 | 동일 검색어에 대한 카카오 API 중복 호출 감소 |
| 3 | Redis Rate Limit 장애 정책 명확화 | 미적용 | Redis 장애 시 전체 API 장애로 전파되는 상황 방지 |
| 4 | 상세 경로 응답 크기 제어 | 미적용 | 후보 경로와 좌표가 많을 때 직렬화·메모리·네트워크 비용 감소 |
| 4 | 반복 검증 로그 제어 및 메트릭 전환 | 미적용 | 대규모 요청에서 로그 I/O와 저장 비용 증가 방지 |

---

## 3. 1순위 해결: DB 트랜잭션과 외부 API 호출 분리

### 3.1 기존 문제

기존 `CourseStartDirectionsService`에는 클래스 단위의 읽기 전용 트랜잭션이 적용되어 있었다.

```java
@Service
@Transactional(readOnly = true)
public class CourseStartDirectionsService {
    // DB 조회와 카카오 API 호출을 모두 수행
}
```

따라서 하나의 서비스 메서드에서 다음 과정이 모두 실행됐다.

```text
트랜잭션 시작
  → DB에서 코스 정보 조회
  → 카카오 길찾기 API 요청 및 응답 대기
  → 응답 변환
트랜잭션 종료
```

카카오 API의 응답이 지연되면 DB 작업은 이미 끝났는데도 트랜잭션과 영속성 컨텍스트가 불필요하게 유지된다. 데이터베이스 및 커넥션 설정에 따라 커넥션 반환도 늦어질 수 있기 때문에 동시 요청이 많아질수록 커넥션 풀 고갈 위험이 커진다.

### 3.2 해결 방법

DB 출발점 조회만 담당하는 `CourseStartPointQueryService`를 별도의 Spring Bean으로 분리하고, 해당 메서드에만 읽기 전용 트랜잭션을 적용했다.

```java
@Service
public class CourseStartPointQueryService {

    private final CourseNavigationRepository repository;

    @Transactional(readOnly = true)
    public CourseStartPoint getStartPoint(Long courseId) {
        CourseStartPointView view = repository.findStartPointByCourseId(courseId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.COURSE_NOT_FOUND)
                );

        return new CourseStartPoint(
                view.getCourseId(),
                view.getName(),
                view.getStartLat(),
                view.getStartLng()
        );
    }
}
```

길찾기 서비스에서는 클래스 단위 `@Transactional`을 제거하고 조회 서비스를 호출한 다음 카카오 API를 호출한다.

```java
@Service
public class CourseStartDirectionsService {

    private final CourseStartPointQueryService startPointQueryService;
    private final KakaoDirectionsClient directionsClient;

    public CourseStartDirectionsResponse getDirectionsToStart(...) {
        CourseStartPoint startPoint =
                startPointQueryService.getStartPoint(courseId);

        // 출발점 조회 트랜잭션이 종료된 후 외부 API 호출
        KakaoRouteDirectionsResponse kakaoResponse = directionsClient.getWalk(
                currentLatitude,
                currentLongitude,
                startPoint.latitude(),
                startPoint.longitude(),
                destinationName
        );

        return routeResponse(
                startPoint,
                destinationName,
                DirectionsMode.WALK,
                kakaoResponse
        );
    }
}
```

별도 서비스로 분리한 이유는 동일 클래스 내부에서 `@Transactional` 메서드를 직접 호출하면 Spring의 트랜잭션 프록시를 거치지 않아 트랜잭션 경계가 의도대로 적용되지 않을 수 있기 때문이다.

### 3.3 변경 후 흐름

```text
CourseStartDirectionsService
  → CourseStartPointQueryService 호출
      → 읽기 전용 트랜잭션 시작
      → 출발점 DB 조회
      → 불변 값 객체로 변환
      → 트랜잭션 종료
  → 카카오 길찾기 API 호출
  → 카카오 응답 검증 및 프론트 응답으로 변환
```

### 3.4 기대 효과

- 카카오 API 응답 시간과 DB 트랜잭션 유지 시간을 분리한다.
- 느린 외부 API 요청이 DB 커넥션 풀에 미치는 영향을 줄인다.
- DB 조회 책임과 외부 API 연동 책임이 분리되어 장애 원인과 성능 지표를 구분하기 쉬워진다.
- 조회 결과를 `CourseStartPoint` 불변 record로 변환해 트랜잭션 종료 후 JPA projection에 의존하지 않는다.

---

## 4. 1순위 해결: 길찾기용 경량 조회 쿼리 도입

### 4.1 기존 문제

길찾기 API는 코스의 ID, 이름, 출발 위도와 경도만 필요하지만 기존에는 내부 내비게이션용 `findNavigationByCourseId()`를 재사용했다.

이 쿼리는 다음과 같은 전체 경로 조회 및 공간 연산까지 실행한다.

```sql
ST_AsGeoJSON(c.path)
ST_NPoints(c.path)
ST_SRID(c.path)
GeometryType(c.path)
ST_IsValid(c.path)
ST_IsEmpty(c.path)
ST_Length(c.path::geography)
ST_Distance(...)
```

경로 좌표가 많거나 요청량이 증가하면 필요하지 않은 공간 연산, GeoJSON 생성, DB와 애플리케이션 사이의 데이터 전송이 반복된다.

### 4.2 해결 방법

길찾기에 필요한 네 개의 컬럼만 반환하는 projection과 전용 쿼리를 추가했다.

```java
public interface CourseStartPointView {
    Long getCourseId();
    String getName();
    Double getStartLat();
    Double getStartLng();
}
```

```java
@Query(value = """
          SELECT
              c.course_id AS courseId,
              c.name AS name,
              ST_Y(COALESCE(c.start_point, ST_StartPoint(c.path))) AS startLat,
              ST_X(COALESCE(c.start_point, ST_StartPoint(c.path))) AS startLng
          FROM course c
          WHERE c.course_id = :courseId
          """, nativeQuery = true)
Optional<CourseStartPointView> findStartPointByCourseId(
        @Param("courseId") Long courseId
);
```

`start_point`가 없는 기존 데이터에 대해서는 `ST_StartPoint(path)`를 사용하는 기존 fallback 동작을 유지했다.

### 4.3 기대 효과

- 길찾기 요청마다 수행되던 불필요한 PostGIS 검증 및 거리 계산을 제거한다.
- 전체 경로의 GeoJSON을 생성하거나 애플리케이션으로 전달하지 않는다.
- 코스 경로가 길어져도 출발점 조회 응답 크기는 일정하게 유지된다.
- 내부 코스 안내 쿼리와 외부 길찾기 쿼리가 각 사용 목적에 맞게 분리된다.

### 4.4 검증 내용

다음 테스트를 추가하거나 수정했다.

- 전용 쿼리가 코스 ID, 이름, 출발 위도와 경도를 반환하는지 검증
- `start_point`가 없으면 `path`의 첫 좌표를 반환하는지 검증
- projection이 트랜잭션 안에서 `CourseStartPoint`로 변환되는지 검증
- 존재하지 않는 코스가 `COURSE_NOT_FOUND`로 처리되는지 검증
- 기존 도보, 자전거, 대중교통 길찾기 동작이 유지되는지 회귀 테스트

전체 백엔드 테스트 결과는 다음과 같다.

```text
BUILD SUCCESSFUL
```

---

## 5. 2순위 해결: 코스 내부 내비게이션 결과 캐싱

코스 경로는 조회 빈도에 비해 수정 빈도가 낮으므로 캐시 효율이 높다. `courseId`를 키로 내부 내비게이션 응답을 캐싱하고 코스 수정·삭제 시 해당 키를 제거하는 방법을 고려한다.

현재 단일 서버 구성을 기준으로 Caffeine 로컬 캐시를 적용했다.

```java
@Cacheable(
        cacheNames = "courseNavigation",
        key = "#courseId",
        sync = true
)
public CourseNavigationResponse getNavigation(Long courseId) {
    // DB 조회, 경로 검증, GeoJSON 역직렬화
}
```

`sync = true`를 사용해 캐시가 비어 있을 때 동일한 `courseId` 요청이 동시에 들어와도 한 요청만 값을 계산하고, 나머지 요청은 그 결과를 공유하도록 했다. 캐시에는 DB projection이 아닌 검증과 GeoJSON 역직렬화를 마친 최종 응답 DTO를 저장한다.

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterWrite=30m
```

- 최대 500개 엔트리로 애플리케이션 메모리 사용량을 제한한다.
- 저장 후 30분이 지나면 만료시켜 장기간 오래된 데이터가 남지 않게 한다.
- 조회 중 예외가 발생한 결과는 캐싱되지 않는다.

현재 백엔드에는 코스 수정·삭제 API가 없으므로 명시적 캐시 제거는 아직 추가하지 않았다. 해당 기능이 추가되면 성공한 쓰기 작업에 `@CacheEvict(cacheNames = "courseNavigation", key = "#courseId")`를 반드시 함께 적용해야 한다. 서버가 여러 대로 확장되면 각 인스턴스의 캐시가 서로 다를 수 있으므로 Redis 분산 캐시 또는 캐시 무효화 이벤트 방식으로 전환을 검토한다.

### 5.1 JMeter 적용 전후 측정

동일한 로컬 환경에서 course ID 42의 284개 좌표 경로를 대상으로 다음 조건을 사용했다.

- 스레드 50개, ramp-up 5초, 스레드당 100회
- 회당 총 5,000건, 적용 전·후 각각 3회
- View Results Tree 없이 CLI(non-GUI) 모드 실행
- 오류율은 적용 전·후 모두 0%

| 지표 | 적용 전 15,000건 | 적용 후 15,000건 | 변화 |
|---|---:|---:|---:|
| 평균 응답시간 | 50.50ms | 0.83ms | 98.4% 감소 |
| p50 | 48ms | 1ms | 97.9% 감소 |
| p95 | 98ms | 2ms | 98.0% 감소 |
| p99 | 135ms | 6ms | 95.6% 감소 |
| 최대 응답시간 | 780ms | 95ms | 87.8% 감소 |
| 3회 평균 처리량 | 약 538 req/s | 약 1,003 req/s | 약 86.5% 증가 |

캐시 적용 전 각 실행의 평균 응답시간은 JVM과 DB 워밍업 영향으로 60ms, 51ms, 39ms의 편차가 있었다. 따라서 특정 1회의 수치가 아니라 3회 15,000건을 합산해 응답시간 분포를 비교했다.

이번 결과는 같은 인기 코스를 반복 조회해 캐시 적중률이 높은 시나리오의 효과다. 실제 운영 효과는 서로 다른 코스의 요청 비율, 캐시 적중률, 경로 크기와 서버 대수에 따라 달라지므로 운영 환경에서는 Caffeine 적중률과 eviction 수를 메트릭으로 함께 관찰해야 한다.

### 5.2 검증

캐시 프록시를 포함한 테스트에서 같은 `courseId`를 두 번 요청하고 repository 조회가 한 번만 실행되는지 검증했다. 전체 백엔드 테스트도 성공했다.


## 6. 2순위: 공간 데이터 검증을 쓰기 시점으로 이동

현재 내부 내비게이션 조회는 `ST_IsValid`, `ST_IsEmpty`, SRID, geometry type 및 계산 거리 등을 읽을 때 확인한다. 코스가 등록되거나 수정될 때 한 번 검증하고 검증된 데이터만 저장하면 반복 조회 비용을 줄일 수 있다.

적용 방법은 다음과 같다.

- 코스 생성·수정 서비스에서 geometry 유효성 검증
- DB CHECK 제약조건으로 SRID와 geometry type 보장
- 계산 거리가 자주 필요하면 쓰기 시 계산해 컬럼에 저장
- 기존 비정상 데이터는 마이그레이션 과정에서 정리

## 7. 2순위: 길찾기 응답 압축

길찾기 응답은 모든 `path.points`를 `[경도, 위도]` 형태로 전달하므로 좌표가 많을수록 JSON 크기가 커진다. 우선 HTTP 압축을 적용하면 API 계약을 변경하지 않고 네트워크 전송량을 줄일 수 있다.

```yaml
server:
  compression:
    enabled: true
    min-response-size: 2KB
```

적용 후에는 압축 전후 응답 바이트, 서버 CPU 사용량 및 모바일 환경의 응답 시간을 함께 측정해야 한다.

## 8. 3순위: 외부 HTTP 연결 관리와 장애 격리

동시 요청이 많을 때는 요청마다 연결 비용을 반복하지 않도록 연결 풀을 지원하는 HTTP 클라이언트를 검토한다. 카카오 API 지연이나 장애가 전체 서버로 전파되지 않도록 다음 정책도 함께 필요하다.

- connect timeout과 read timeout 분리
- 외부 API 동시 호출 수를 제한하는 bulkhead
- 연속 장애 시 호출을 빠르게 차단하는 circuit breaker
- 멱등성이 보장되고 일시적인 오류인 경우에만 제한적 retry
- 실패 유형을 내부 서버 오류와 외부 API 오류로 구분

무조건적인 재시도는 카카오 장애 시 요청량을 증폭할 수 있으므로 피한다.

## 9. 3순위: 장소 검색 최적화

장소 검색은 사용자의 입력마다 호출될 가능성이 있어 다음 최적화 효과가 크다.

- 프론트에서 300~500ms debounce 적용
- 최소 검색 글자 수 제한
- 공백과 대소문자 등 검색어 정규화
- 동일 검색어 결과의 짧은 TTL 캐싱
- 동시에 들어온 동일 검색어 요청을 하나로 합치는 single-flight
- 현재 좌표, 반경 또는 영역을 사용해 검색 범위 제한

카카오 응답을 저장하거나 장기간 캐싱할 때는 먼저 카카오 API 이용약관과 데이터 보관 조건을 확인해야 한다.

## 10. 3순위: Redis Rate Limit 장애 정책

현재 Lua 스크립트를 사용한 고정 윈도우 Rate Limit은 원자적으로 동작하지만 윈도우 경계에서 순간적으로 허용량의 두 배에 가까운 요청이 통과할 수 있다.

대규모 운영 전 다음 내용을 결정해야 한다.

- 더 정확한 제한이 필요하면 sliding window 또는 token bucket 검토
- Redis 장애 시 요청을 허용할지(fail-open), 차단할지(fail-closed) 결정
- Redis 예외를 그대로 노출하지 않고 정의된 503 응답 등으로 변환
- 사용자, IP, API별 키 정책과 TTL 모니터링

## 11. 4순위: 상세 경로 응답 크기 제어

대중교통 후보와 모든 구간 좌표를 한 번에 반환하면 JSON 직렬화 비용, 힙 메모리 사용량 및 네트워크 비용이 증가할 수 있다.

사용량이 증가한 뒤 다음 방법을 측정 기반으로 적용한다.

- 최초 응답은 경로 후보 요약만 반환하고 선택한 후보의 상세 좌표를 별도 조회
- 반환하는 후보 경로 개수 제한
- 비정상적으로 많은 좌표에 대한 상한 설정
- 모바일 화면에 필요한 좌표 정밀도와 단순화 허용 범위 검토

좌표를 임의로 생략하면 경로 정확도가 떨어지므로 실제 응답 크기와 렌더링 요구사항을 측정한 후 적용해야 한다.

## 12. 4순위: 로그 및 메트릭 최적화

동일한 잘못된 공간 데이터가 요청될 때마다 경고 로그를 기록하면 대규모 환경에서 로그 I/O와 저장 비용이 증가한다.

- 데이터 유효성 검증을 쓰기 시점으로 이동
- 반복 로그 sampling 또는 rate limit 적용
- 요청 수, 외부 API 지연, 실패율, 캐시 적중률은 로그보다 메트릭으로 집계
- 알림은 개별 오류가 아니라 일정 시간 동안의 오류율을 기준으로 설정

---

## 13. 후속 성능 검증 계획

현재 1순위 개선은 구조적 병목을 제거한 것이며, 개선 폭을 수치로 설명하려면 동일한 조건에서 변경 전후 부하 테스트가 필요하다.

측정할 핵심 지표는 다음과 같다.

| 구분 | 측정 지표 |
|---|---|
| API | 평균 응답 시간, p95, p99, 초당 처리량, 오류율 |
| DB | 쿼리 실행 시간, 활성 커넥션 수, 커넥션 대기 시간, 트랜잭션 유지 시간 |
| 외부 API | 카카오 호출 횟수, 평균·p95 지연, timeout 및 오류율 |
| JVM | CPU, 힙 사용량, GC 횟수와 정지 시간 |
| 네트워크 | 압축 전후 응답 크기와 전송 시간 |
| 캐시 | hit ratio, miss 수, eviction 수 |

외부 API 응답 시간을 고정한 stub 서버를 사용하면 DB 및 애플리케이션 구조 변경의 효과와 카카오 네트워크 변동을 분리해서 측정할 수 있다.

---

## 14. 포트폴리오 활용 문장 예시

> 코스 출발점 길찾기 API에서 DB 조회와 외부 지도 API 호출이 하나의 읽기 트랜잭션으로 묶여 있어, 외부 API 지연 시 DB 자원이 불필요하게 유지될 수 있는 구조를 발견했습니다. 출발점 조회를 별도 트랜잭션 서비스로 분리하고 조회 결과를 불변 record로 변환한 뒤 트랜잭션 밖에서 카카오 API를 호출하도록 개선했습니다. 또한 길찾기에 필요하지 않은 GeoJSON 생성과 PostGIS 거리·유효성 연산을 수행하던 기존 쿼리를 ID·이름·출발 좌표만 조회하는 전용 projection 쿼리로 교체했습니다. 이를 통해 외부 API 지연이 DB 커넥션 풀에 미치는 영향을 줄이고, 요청당 공간 연산과 데이터 전송 비용을 낮출 수 있는 구조를 만들었으며 전체 회귀 테스트로 기존 기능이 유지됨을 검증했습니다.

면접에서는 실제 측정 전 기대 효과와 부하 테스트로 확인된 수치를 구분해서 설명해야 한다.
