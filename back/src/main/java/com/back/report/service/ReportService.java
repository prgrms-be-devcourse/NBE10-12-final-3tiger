package com.back.report.service;

import com.back.comment.domain.Comment;
import com.back.comment.repository.CommentRepository;
import com.back.global.error.ApiException;
import com.back.post.domain.Post;
import com.back.post.repository.PostRepository;
import com.back.report.domain.Report;
import com.back.report.domain.ReportReason;
import com.back.report.domain.ReportTargetType;
import com.back.report.repository.ReportRepository;
import com.back.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reports;
    private final UserRepository users;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final int hideThreshold;

    public ReportService(ReportRepository reports, UserRepository users, PostRepository posts,
                         CommentRepository comments,
                         @Value("${app.report.hide-threshold:5}") int hideThreshold) {
        this.reports = reports;
        this.users = users;
        this.posts = posts;
        this.comments = comments;
        this.hideThreshold = hideThreshold;
    }

    /**
     * 신고를 접수한다. 이미 같은 대상을 신고했으면 에러 없이 현재 상태를 그대로 반환한다(멱등).
     * 대상별 신고 수가 임계치에 도달하면 POST/COMMENT 는 자동으로 hidden 처리한다.
     */
    @Transactional
    public ReportResult report(Long reporterId, ReportTargetType targetType, Long targetId, ReportReason reason) {
        validateTarget(reporterId, targetType, targetId);

        if (!reports.existsByReporter_IdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
            try {
                reports.save(new Report(users.getReferenceById(reporterId), targetType, targetId, reason));
            } catch (DataIntegrityViolationException e) {
                // uk_report_reporter_target 위반 = 동시 중복 신고 → 멱등 처리
            }
        }

        long reportCount = reports.countByTargetTypeAndTargetId(targetType, targetId);
        boolean hidden = hideIfThresholdReached(targetType, targetId, reportCount);
        return new ReportResult(targetType, targetId, reportCount, hidden);
    }

    private void validateTarget(Long reporterId, ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST -> {
                Post post = posts.findById(targetId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 게시물입니다."));
                if (post.getUser().getId().equals(reporterId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "본인 게시물은 신고할 수 없습니다.");
                }
            }
            case COMMENT -> {
                Comment comment = comments.findById(targetId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 댓글입니다."));
                if (comment.getUser().getId().equals(reporterId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "본인 댓글은 신고할 수 없습니다.");
                }
            }
            case USER -> {
                if (reporterId.equals(targetId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없습니다.");
                }
                if (!users.existsById(targetId)) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다.");
                }
            }
        }
    }

    private boolean hideIfThresholdReached(ReportTargetType targetType, Long targetId, long reportCount) {
        if (reportCount < hideThreshold) {
            return false;
        }
        // USER 는 스키마상 숨김 대상이 아니라 신고만 누적한다.
        return switch (targetType) {
            case POST -> {
                posts.hide(targetId);
                yield true;
            }
            case COMMENT -> {
                comments.hide(targetId);
                yield true;
            }
            case USER -> false;
        };
    }

    public record ReportResult(ReportTargetType targetType, Long targetId, long reportCount, boolean hidden) {}
}
