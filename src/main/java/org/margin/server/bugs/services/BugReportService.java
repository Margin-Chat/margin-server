package org.margin.server.bugs.services;

import org.margin.server.bugs.entities.BugReport;
import org.margin.server.bugs.repositories.BugReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BugReportService {
    private final BugReportRepository bugReportRepository;

    public BugReportService(BugReportRepository bugReportRepository) {
        this.bugReportRepository = bugReportRepository;
    }

    @Transactional
    public void createBug(String title, String description, Long reportingUserId) {
        BugReport bugReport = new BugReport();
        bugReport.setTitle(title);
        bugReport.setDescription(description);
        bugReport.setUserId(reportingUserId);
        bugReport.setCreatedAt(Instant.now());
        bugReportRepository.save(bugReport);
    }
}
