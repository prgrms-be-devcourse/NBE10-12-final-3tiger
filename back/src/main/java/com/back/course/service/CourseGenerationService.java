package com.back.course.service;

import com.back.bookmark.service.BookmarkService;
import com.back.course.dto.GenerateCandidate;
import com.back.course.dto.GenerateRequest;
import com.back.course.dto.GenerateResponse;
import com.back.course.dto.SaveCourseRequest;
import com.back.course.map.event.CourseMapImageRequested;
import com.back.course.repository.CourseGenerationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseGenerationService {

    private static final int TARGET_COUNT = 3;
    private static final int MAX_CANDIDATES = 8;
    private static final int ONEWAY_CANDIDATE_COUNT = 1;

    // 거리별 오차 임계값 — 짧은 loop은 그래프 오버헤드 흡수가 어려워 관대하게, 긴 loop은 엄격히
    private static final BigDecimal ERROR_LIMIT_SHORT = new BigDecimal("25.0");  // ≤1500m (1km급)
    private static final BigDecimal ERROR_LIMIT_MID   = new BigDecimal("15.0");  // ≤4000m (3km급)
    private static final BigDecimal ERROR_LIMIT_LONG  = new BigDecimal("10.0");  // >4000m (5km/8km)

    private static BigDecimal errorLimitFor(int distanceM) {
        if (distanceM <= 1500) return ERROR_LIMIT_SHORT;
        if (distanceM <= 4000) return ERROR_LIMIT_MID;
        return ERROR_LIMIT_LONG;
    }

    private final CourseGenerationRepository repo;
    private final BookmarkService bookmarkService;
    private final ApplicationEventPublisher eventPublisher;

    public CourseGenerationService(
            CourseGenerationRepository repo,
            BookmarkService bookmarkService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repo = repo;
        this.bookmarkService = bookmarkService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 후보 코스 계산 (DB 저장 X).
     * - 순환: 후보 3개 시도 (기존 로직)
     * - 편도: dijkstra로 단일 최적 경로 → 후보 1개
     */
    public GenerateResponse generate(GenerateRequest req) {
        String persona = req.persona() != null ? req.persona().name() : null;

        if (req.isOneway()) {
            return generateOneway(req, persona);
        }
        return generateLoop(req, persona);
    }

    private GenerateResponse generateLoop(GenerateRequest req, String persona) {
        if (req.distanceM() == null) {
            throw new IllegalArgumentException("순환 코스는 distanceM이 필수입니다.");
        }

        BigDecimal errorLimit = errorLimitFor(req.distanceM());
        List<GenerateCandidate> ok = new ArrayList<>();
        for (int idx = 0; idx < MAX_CANDIDATES && ok.size() < TARGET_COUNT; idx++) {
            var row = repo.generateOnly(
                    req.lng(), req.lat(), req.distanceM(), req.atOrNow(), idx, persona
            );
            if (row.isEmpty()) continue;

            var r = row.get();
            if (r.errorPct().compareTo(errorLimit) <= 0) {
                ok.add(new GenerateCandidate(
                        r.path(), r.totalM(), r.avgScore(), r.errorPct(), r.regionCode()
                ));
            }
        }

        return new GenerateResponse(ok, TARGET_COUNT, ok.size());
    }

    private GenerateResponse generateOneway(GenerateRequest req, String persona) {
        var row = repo.generateOnewayOnly(
                req.lng(), req.lat(), req.endLng(), req.endLat(), req.atOrNow(), persona
        );

        List<GenerateCandidate> ok = new ArrayList<>();
        row.ifPresent(r -> ok.add(new GenerateCandidate(
                r.path(), r.totalM(), r.avgScore(), null, r.regionCode()
        )));

        return new GenerateResponse(ok, ONEWAY_CANDIDATE_COUNT, ok.size());
    }

    /** 사용자가 선택한 코스 저장 → courseId */
    @Transactional
    public Long save(Long userId, SaveCourseRequest req) {
        CoursePathValidator.validateForWrite(req.path());
        boolean isLoop = req.isLoopOrDefault();
        Long courseId = repo.saveFromPath(
                req.path(), req.regionCode(), isLoop, req.endLng(), req.endLat(), req.name()
        );
        repo.markMapImagePending(courseId);
        bookmarkService.add(userId, courseId);
        eventPublisher.publishEvent(new CourseMapImageRequested(courseId, req.path()));
        return courseId;
    }
}
