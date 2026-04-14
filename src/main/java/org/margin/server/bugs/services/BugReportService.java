package org.margin.server.bugs.services;

import org.margin.server.bugs.entities.BugReport;
import org.margin.server.bugs.repositories.BugReportRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BugReportService {
    private final BugReportRepository bugReportRepository;

    public BugReportService(BugReportRepository bugReportRepository) {
        this.bugReportRepository = bugReportRepository;
    }

    @Transactional
    public void createBug(String title, String description, User reportingUser) {
        BugReport bugReport = new BugReport();
        bugReport.setTitle(title);
        bugReport.setDescription(description);
        bugReport.setUser(reportingUser);
        bugReportRepository.save(bugReport);
    }
}
