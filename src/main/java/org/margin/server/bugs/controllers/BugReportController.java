package org.margin.server.bugs.controllers;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.bugs.models.BugReportRequest;
import org.margin.server.bugs.services.BugReportService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class BugReportController {

    private final BugReportService bugReportService;

    public BugReportController(BugReportService bugReportService) {
        this.bugReportService = bugReportService;
    }

    @PostMapping("/report_bug")
    public ResponseEntity<Void> reportBug(@RequestBody BugReportRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        bugReportService.createBug(request.bugTitle(), request.bugDescription(), user.id());
        return ResponseEntity.ok().build();
    }
}
