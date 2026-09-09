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
import com.back.hazard.dto.HazardResolutionResponse;
import com.back.hazard.repository.HazardConfirmationRepository;
import com.back.hazard.repository.HazardReportRepository;
import com.back.hazard.repository.HazardRepository;
import com.back.hazard.repository.HazardResolutionRepository;
import com.back.point.service.PointRewardService;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class HazardService {

    private static final long ACTIVATION_REPORTER_THRESHOLD = 3L;
    private static final long RESOLUTION_REPORTER_THRESHOLD = 3L;

    private final HazardRepository hazardRepository;
    private final HazardReportRepository hazardReportRepository;
    private final HazardConfirmationRepository hazardConfirmationRepository;
    private final HazardResolutionRepository hazardResolutionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final HazardMatchingService hazardMatchingService;
    private final PointRewardService pointRewardService;
    private final EntityManager entityManager;

    public HazardService(
            HazardRepository hazardRepository,
            HazardReportRepository hazardReportRepository,
            HazardConfirmationRepository hazardConfirmationRepository,
            HazardResolutionRepository hazardResolutionRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            HazardMatchingService hazardMatchingService,
            PointRewardService pointRewardService,
            EntityManager entityManager
    ) {
        this.hazardRepository = hazardRepository;
        this.hazardReportRepository = hazardReportRepository;
        this.hazardConfirmationRepository = hazardConfirmationRepository;
        this.hazardResolutionRepository = hazardResolutionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.hazardMatchingService = hazardMatchingService;
        this.pointRewardService = pointRewardService;
        this.entityManager = entityManager;
    }

    @Transactional
    public HazardCreateResponse create(Long userId, Long courseId, HazardCreateRequest request) {
        User reporter = findActiveUser(userId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        var matchingHazard = hazardMatchingService.findMatchingHazard(
                courseId,
                request.hazardType(),
                request.latitude(),
                request.longitude()
        );
        Hazard hazard;
        if (matchingHazard.isPresent()) {
            var lockedHazard = findHazardForUpdateIfPresent(matchingHazard.get().getId());
            if (lockedHazard.isPresent()) {
                hazard = lockedHazard.get();
            } else {
                hazard = hazardRepository.save(new Hazard(course, request.hazardType()));
            }
        } else {
            hazard = hazardRepository.save(new Hazard(course, request.hazardType()));
        }

        if (matchingHazard.isPresent()
                && hazard.getId().equals(matchingHazard.get().getId())) {
            if (hazardReportRepository.existsByHazard_IdAndReporter_Id(
                    hazard.getId(), userId)) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 신고한 위험입니다.");
            }
        }

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
        Hazard hazard = findHazardForUpdate(hazardId);
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
    public HazardResolutionResponse resolve(Long userId, Long hazardId) {
        Hazard hazard = findHazardForUpdate(hazardId);
        if (hazard.getStatus() != HazardStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "활성 상태의 위험만 해결 확인할 수 있습니다.");
        }
        User user = findActiveUser(userId);
        if (hazardResolutionRepository.existsByHazard_IdAndUser_Id(hazardId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 해결 확인한 위험입니다.");
        }
        com.back.hazard.domain.HazardResolution resolution;
        try {
            resolution = hazardResolutionRepository.saveAndFlush(
                    new com.back.hazard.domain.HazardResolution(hazard, user));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 해결 확인한 위험입니다.");
        }
        pointRewardService.rewardHazardResolution(userId, resolution.getId());
        long count = hazardResolutionRepository.countByHazard_Id(hazardId);
        if (count >= RESOLUTION_REPORTER_THRESHOLD) {
            hazard.resolve();
            pointRewardService.rewardHazardResolved(
                    hazardId, hazardResolutionRepository.findDistinctUserIdsByHazardId(hazardId));
        }
        return new HazardResolutionResponse(hazard.getStatus() == HazardStatus.RESOLVED, count);
    }

    @Transactional
    public void deleteMyReport(Long userId, Long hazardId) {
        Hazard hazard = findHazardForUpdate(hazardId);
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
            hazardResolutionRepository.deleteByHazard_Id(hazardId);
            hazardResolutionRepository.flush();
            hazardRepository.delete(hazard);
            return;
        }

        hazard.updateStatusByReporterCount(
                distinctReporterCount,
                ACTIVATION_REPORTER_THRESHOLD
        );
    }

    public List<HazardResponse> getActiveHazards(Long courseId, Long userId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다.");
        }

        return hazardReportRepository.findVisibleHazardResponsesByCourseId(
                courseId,
                HazardStatus.ACTIVE,
                HazardStatus.PENDING,
                userId
        );
    }

    private void saveReportAndUpdateStatus(
            Hazard hazard,
            User reporter,
            String severity,
            String content,
            double latitude,
            double longitude
    ) {
        HazardReport report = new HazardReport(
                hazard,
                reporter,
                severity,
                content,
                latitude,
                longitude
        );
        try {
            hazardReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 신고한 위험입니다.");
        }

        long distinctReporterCount = hazardReportRepository
                .countDistinctReportersByHazardId(hazard.getId());
        boolean wasPending = hazard.getStatus() == HazardStatus.PENDING;
        hazard.updateStatusByReporterCount(
                distinctReporterCount,
                ACTIVATION_REPORTER_THRESHOLD
        );

        if (wasPending && hazard.getStatus() == HazardStatus.ACTIVE) {
            pointRewardService.rewardHazardActivation(
                    hazard.getId(),
                    hazardReportRepository.findDistinctReporterIdsByHazardId(hazard.getId())
            );
        }

        pointRewardService.rewardHazardReport(reporter.getId(), report.getId());
    }

    private Hazard findHazard(Long hazardId) {
        return hazardRepository.findById(hazardId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 위험입니다."
                ));
    }

    private Hazard findHazardForUpdate(Long hazardId) {
        return findHazardForUpdateIfPresent(hazardId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 위험입니다."
                ));
    }

    private Optional<Hazard> findHazardForUpdateIfPresent(Long hazardId) {
        Optional<Hazard> hazard = hazardRepository.findByIdForUpdate(hazardId);
        hazard.ifPresent(entityManager::refresh);
        return hazard;
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "존재하지 않는 사용자입니다."
                ));
    }
}
