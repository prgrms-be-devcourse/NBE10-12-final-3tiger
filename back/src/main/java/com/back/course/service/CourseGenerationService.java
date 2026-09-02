package com.back.course.service;

import com.back.bookmark.service.BookmarkService;
import com.back.course.dto.GenerateCandidate;
import com.back.course.dto.GenerateRequest;
import com.back.course.dto.GenerateResponse;
import com.back.course.dto.SaveCourseRequest;
import com.back.course.repository.CourseGenerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseGenerationService {

    private static final int TARGET_COUNT = 3;
    private static final int MAX_CANDIDATES = 6;
    private static final BigDecimal ERROR_LIMIT = new BigDecimal("10.0");
    private static final int ONEWAY_CANDIDATE_COUNT = 1;

    private final CourseGenerationRepository repo;
    private final BookmarkService bookmarkService;

    public CourseGenerationService(CourseGenerationRepository repo, BookmarkService bookmarkService) {
        this.repo = repo;
        this.bookmarkService = bookmarkService;
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

        List<GenerateCandidate> ok = new ArrayList<>();
        for (int idx = 0; idx < MAX_CANDIDATES && ok.size() < TARGET_COUNT; idx++) {
            var row = repo.generateOnly(
                    req.lng(), req.lat(), req.distanceM(), req.atOrNow(), idx, persona
            );
            if (row.isEmpty()) continue;

            var r = row.get();
            if (r.errorPct().compareTo(ERROR_LIMIT) <= 0) {
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
        boolean isLoop = req.isLoopOrDefault();
        Long courseId = repo.saveFromPath(
                req.path(), req.regionCode(), isLoop, req.endLng(), req.endLat(), req.name()
        );
        bookmarkService.add(userId, courseId);
        return courseId;
    }
}
