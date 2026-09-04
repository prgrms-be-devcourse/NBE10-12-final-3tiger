package com.back.hazard.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.error.ApiException;
import com.back.hazard.domain.Hazard;
import com.back.hazard.domain.HazardConfirmation;
import com.back.hazard.domain.HazardReport;
import com.back.hazard.domain.HazardStatus;
import com.back.hazard.dto.HazardConfirmationResponse;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardCreateResponse;
import com.back.hazard.dto.HazardReportCreateRequest;
import com.back.hazard.dto.HazardResponse;
import com.back.hazard.repository.HazardConfirmationRepository;
import com.back.hazard.repository.HazardReportRepository;
import com.back.hazard.repository.HazardRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HazardService {

    private static final long ACTIVATION_REPORTER_THRESHOLD = 3L;

    private final HazardRepository hazardRepository;
    private final HazardReportRepository hazardReportRepository;
    private final HazardConfirmationRepository hazardConfirmationRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public HazardService(
            HazardRepository hazardRepository,
            HazardReportRepository hazardReportRepository,
            HazardConfirmationRepository hazardConfirmationRepository,
            CourseRepository courseRepository,
            UserRepository userRepository
    ) {
        this.hazardRepository = hazardRepository;
        this.hazardReportRepository = hazardReportRepository;
        this.hazardConfirmationRepository = hazardConfirmationRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public HazardCreateResponse create(Long userId, Long courseId, HazardCreateRequest request) {
        User reporter = findActiveUser(userId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        Hazard hazard = hazardRepository.save(new Hazard(course, request.hazardType()));

        saveReportAndUpdateStatus(
                hazard,
                reporter,
                request.severity(),
                request.content(),
                request.latitude(),
                request.longitude()
        );

        return new HazardCreateResponse(hazard.getId());
    }

    @Transactional
    public void addReport(Long userId, Long hazardId, HazardReportCreateRequest request) {
        Hazard hazard = findHazard(hazardId);
        User reporter = findActiveUser(userId);

        if (hazardReportRepository.existsByHazard_IdAndReporter_Id(hazardId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 신고한 위험입니다.");
        }

        saveReportAndUpdateStatus(
                hazard,
                reporter,
                request.severity(),
                request.content(),
                request.latitude(),
                request.longitude()
        );
    }

    @Transactional
    public HazardConfirmationResponse confirm(Long userId, Long hazardId) {
        Hazard hazard = findHazard(hazardId);
        User user = findActiveUser(userId);

        if (hazardConfirmationRepository.existsByHazard_IdAndUser_Id(hazardId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 확인한 위험입니다.");
        }

        try {
            hazardConfirmationRepository.saveAndFlush(new HazardConfirmation(hazard, user));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 확인한 위험입니다.");
        }

        return new HazardConfirmationResponse(
                true,
                hazardConfirmationRepository.countByHazard_Id(hazardId)
        );
    }

    @Transactional
    public void deleteMyReport(Long userId, Long hazardId) {
        Hazard hazard = findHazard(hazardId);
        findActiveUser(userId);
        HazardReport report = hazardReportRepository
                .findByHazard_IdAndReporter_Id(hazardId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "해당 위험에 등록한 신고가 없습니다."
                ));

        hazardReportRepository.delete(report);
        hazardReportRepository.flush();

        long distinctReporterCount = hazardReportRepository
                .countDistinctReportersByHazardId(hazardId);
        if (distinctReporterCount == 0) {
            hazardConfirmationRepository.deleteByHazard_Id(hazardId);
            hazardConfirmationRepository.flush();
            hazardRepository.delete(hazard);
            return;
        }

        hazard.updateStatusByReporterCount(
                distinctReporterCount,
                ACTIVATION_REPORTER_THRESHOLD
        );
    }

    public List<HazardResponse> getActiveHazards(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다.");
        }

        return hazardRepository
                .findByCourse_IdAndStatusOrderByCreatedAtDesc(courseId, HazardStatus.ACTIVE)
                .stream()
                .map(hazard -> HazardResponse.from(
                        hazard,
                        hazardConfirmationRepository.countByHazard_Id(hazard.getId())
                ))
                .toList();
    }

    private void saveReportAndUpdateStatus(
            Hazard hazard,
            User reporter,
            String severity,
            String content,
            double latitude,
            double longitude
    ) {
        try {
            hazardReportRepository.saveAndFlush(new HazardReport(
                    hazard,
                    reporter,
                    severity,
                    content,
                    latitude,
                    longitude
            ));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 신고한 위험입니다.");
        }

        long distinctReporterCount = hazardReportRepository
                .countDistinctReportersByHazardId(hazard.getId());
        hazard.updateStatusByReporterCount(
                distinctReporterCount,
                ACTIVATION_REPORTER_THRESHOLD
        );
    }

    private Hazard findHazard(Long hazardId) {
        return hazardRepository.findById(hazardId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 위험입니다."
                ));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "존재하지 않는 사용자입니다."
                ));
    }
}
