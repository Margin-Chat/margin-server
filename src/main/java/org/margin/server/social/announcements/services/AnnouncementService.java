package org.margin.server.social.announcements.services;

import org.margin.server.social.announcements.models.Announcement;
import org.margin.server.social.announcements.models.dtos.AnnouncementDTO;
import org.margin.server.social.announcements.models.dtos.CreateAnnouncementRequest;
import org.margin.server.social.announcements.repositories.AnnouncementRepository;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final MarginRepository marginRepository;
    private final UserService userService;

    public AnnouncementService(AnnouncementRepository announcementRepository, MarginRepository marginRepository, UserService userService) {
        this.announcementRepository = announcementRepository;
        this.marginRepository = marginRepository;
        this.userService = userService;
    }

    public List<Announcement> getAnnouncementsForMargin(Long marginId) {
        return announcementRepository.getAnnouncementsByMargin_Id(marginId);
    }

    public AnnouncementDTO toDTO(Announcement announcement) {
        return new AnnouncementDTO(
                announcement.getAnnouncementId(),
                announcement.getMargin().getId(),
                userService.toDTO(announcement.getAuthor()),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getCreatedAt()
        );
    }

    public Announcement createNewAnnouncement(CreateAnnouncementRequest createAnnouncementRequest, User author) {
        Announcement announcement = new Announcement();
        announcement.setMargin(marginRepository.findById(createAnnouncementRequest.marginId()).orElseThrow());
        announcement.setAuthor(author);
        announcement.setTitle(createAnnouncementRequest.title());
        announcement.setContent(createAnnouncementRequest.content());
        announcement.setCreatedAt(LocalDateTime.now());
        return announcementRepository.save(announcement);
    }

    public Announcement editAnnouncement(AnnouncementDTO announcementDTO) {
        Announcement announcement = getById(announcementDTO.announcementId());
        announcement.setTitle(announcementDTO.title());
        announcement.setContent(announcementDTO.content());
        announcement.setEditedAt(LocalDateTime.now());
        return announcementRepository.save(announcement);
    }

    public void deleteAnnouncement(Long announcementId) {
        Announcement announcement = getById(announcementId);
        announcementRepository.delete(announcement);
    }

    public Announcement getById(Long announcementId) {
        return announcementRepository.findById(announcementId).orElseThrow();
    }
}
