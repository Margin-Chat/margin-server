package org.margin.server.social.announcements.repositories;

import org.margin.server.social.announcements.models.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> getAnnouncementsByMargin_Id(Long marginId);
}
