package com.back.course.service;

import com.back.course.dto.GenerateCandidate;
import com.back.course.dto.GenerateRequest;
import com.back.course.dto.GenerateResponse;
import com.back.course.dto.SaveCourseRequest;
import com.back.course.repository.CourseGenerationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseGenerationService {

    private static final int TARGET_COUNT = 3;
    private static final int MAX_CANDIDATES = 6;
    private static final BigDecimal ERROR_LIMIT = new BigDecimal("10.0");

    private final CourseGenerationRepository repo;

    public CourseGenerationService(CourseGenerationRepository repo) {
        this.repo = repo;
    }

    /** 후보 3개 계산 (DB 저장 X) */
    public GenerateResponse generate(GenerateRequest req) {
        List<GenerateCandidate> ok = new ArrayList<>();

        for (int idx = 0; idx < MAX_CANDIDATES && ok.size() < TARGET_COUNT; idx++) {
            var row = repo.generateOnly(
                    req.lng(), req.lat(), req.distanceM(), req.atOrNow(), idx
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

    /** 사용자가 선택한 코스 저장 → courseId */
    public Long save(SaveCourseRequest req) {
        return repo.saveFromPath(req.path(), req.regionCode());
    }
}
