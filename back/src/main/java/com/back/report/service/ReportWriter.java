package com.back.report.service;

import com.back.report.domain.Report;
import com.back.report.repository.ReportRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReportWriter {
    private final ReportRepository reports;
    public ReportWriter(ReportRepository reports) {
        this.reports = reports;
    }

    // 별도(REQUIRES_NEW) 트랜잭션으로 격리: saveAndFlush가 유니크 제약 위반으로 실패해도
    // 호출한 쪽의 바깥 트랜잭션까지 rollback-only로 오염되지 않도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySave(Report report) {
        reports.saveAndFlush(report);
    }
}
