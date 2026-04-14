package org.margin.server.bugs.repositories;

import org.margin.server.bugs.entities.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugReportRepository extends JpaRepository<BugReport, Long> {
}
