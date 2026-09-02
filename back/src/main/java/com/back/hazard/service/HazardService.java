package com.back.hazard.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.error.ApiException;
import com.back.hazard.domain.Hazard;
import com.back.hazard.dto.HazardCreateRequest;
import com.back.hazard.dto.HazardCreateResponse;
import com.back.hazard.dto.HazardResponse;
import com.back.hazard.dto.HazardUpvoteResponse;
import com.back.hazard.repository.HazardRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HazardService {

    private final HazardRepository hazardRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public HazardService(
            HazardRepository hazardRepository,
            CourseRepository courseRepository,
            UserRepository userRepository
    ) {
        this.hazardRepository = hazardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public HazardCreateResponse create(Long userId, Long courseId, HazardCreateRequest request) {
        User reporter = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다."));
        Hazard hazard = new Hazard(
                course,
                reporter,
                request.hazardType(),
                request.severity(),
                request.content(),
                request.expiresAt()
        );

        return new HazardCreateResponse(hazardRepository.save(hazard).getId());
    }

    @Transactional
    public void delete(Long userId, Long hazardId) {
        Hazard hazard = hazardRepository.findById(hazardId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 위험 신고입니다."));

        if (hazard.getReporter() == null || !userId.equals(hazard.getReporter().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "본인이 등록한 위험 신고만 삭제할 수 있습니다.");
        }

        hazardRepository.delete(hazard);
    }

    @Transactional
    public HazardUpvoteResponse upvote(Long hazardId) {
        Hazard hazard = hazardRepository.findById(hazardId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 위험 신고입니다."));

        hazard.increaseUpvote();
        return new HazardUpvoteResponse(true, hazard.getUpvoteCount());
    }

    public List<HazardResponse> getActiveHazards(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 코스입니다.");
        }

        return hazardRepository
                .findByCourse_IdAndExpiresAtAfterOrderByExpiresAtAsc(courseId, LocalDateTime.now())
                .stream()
                .map(HazardResponse::from)
                .toList();
    }
}
