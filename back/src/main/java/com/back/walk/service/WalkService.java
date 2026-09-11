package com.back.walk.service;

import com.back.bookmark.domain.CourseUsageLog;
import com.back.bookmark.repository.BookmarkRepository;
import com.back.bookmark.repository.CourseUsageLogRepository;
import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.walk.dto.WalkCompletionRequest;
import com.back.walk.dto.WalkRecordResponse;
import com.back.walk.repository.WalkHistoryQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class WalkService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BookmarkRepository bookmarkRepository;
    private final CourseUsageLogRepository usageLogRepository;
    private final WalkHistoryQueryRepository historyRepository;

    public WalkService(
            UserRepository userRepository,
            CourseRepository courseRepository,
            BookmarkRepository bookmarkRepository,
            CourseUsageLogRepository usageLogRepository,
            WalkHistoryQueryRepository historyRepository
    ) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.usageLogRepository = usageLogRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public WalkRecordResponse complete(Long userId, Long courseId, WalkCompletionRequest request) {
        long durationSeconds = Duration.between(request.startedAt(), request.finishedAt()).getSeconds();
        if (durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        LocalDateTime finishedAt = request.finishedAt()
                .atZoneSameInstant(SEOUL_ZONE)
                .toLocalDateTime();

        Integer rating = bookmarkRepository.findByUserIdAndCourseId(userId, courseId)
                .map(bookmark -> {
                    bookmark.recordUsage(finishedAt);
                    return bookmark.getRating();
                })
                .orElse(null);

        CourseUsageLog saved = usageLogRepository.save(new CourseUsageLog(
                user,
                course,
                finishedAt,
                (int) durationSeconds
        ));

        return new WalkRecordResponse(
                saved.getId(),
                course.getId(),
                course.getName(),
                saved.getUsedAt(),
                course.getDistanceM(),
                saved.getDurationSeconds(),
                rating
        );
    }

    public PageResponse<WalkRecordResponse> getMyWalks(Long userId, int page, int size) {
        return historyRepository.findByUserId(userId, page, size);
    }
}
