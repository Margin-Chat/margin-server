package org.margin.server.social.announcements.services;

import org.margin.server.users.api.UserLookup;
import org.margin.server.social.announcements.events.AnnouncementCreatedEvent;
import org.margin.server.users.api.UserSummary;
import org.margin.server.social.announcements.models.Announcement;
import org.margin.server.social.announcements.models.dtos.AnnouncementDTO;
import org.margin.server.social.announcements.models.dtos.CreateAnnouncementRequest;
import org.margin.server.social.announcements.repositories.AnnouncementRepository;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.repositories.MarginRepository;
import org.margin.server.users.services.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserLookup userLookup;
    private final MarginRepository marginRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public AnnouncementService(AnnouncementRepository announcementRepository, MarginRepository marginRepository, UserService userService, ApplicationEventPublisher eventPublisher,
                              UserLookup userLookup) {
        this.announcementRepository = announcementRepository;
        this.marginRepository = marginRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.userLookup = userLookup;
    }

    public List<Announcement> getAnnouncementsForMargin(Long marginId) {
        return announcementRepository.getAnnouncementsByMargin_Id(marginId);
    }

    public AnnouncementDTO toDTO(Announcement announcement) {
        return new AnnouncementDTO(
                announcement.getAnnouncementId(),
                announcement.getMargin().getId(),
                userLookup.dtoOf(announcement.getAuthorId()),
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getCreatedAt()
        );
    }

    public Announcement createAnnouncement(CreateAnnouncementRequest createAnnouncementRequest, Long authorId) {
        Margin margin = marginRepository.findById(createAnnouncementRequest.marginId()).orElseThrow();

        Announcement announcement = new Announcement();
        announcement.setMargin(margin);
        announcement.setAuthorId(authorId);
        announcement.setTitle(createAnnouncementRequest.title());
        announcement.setContent(createAnnouncementRequest.content());
        announcement.setCreatedAt(Instant.now());
        Announcement saved = announcementRepository.save(announcement);

        List<Long> members = margin.getMembers().stream()
                .map(MarginMember::getUserId)
                .toList();

        eventPublisher.publishEvent(
                new AnnouncementCreatedEvent(members, new UserSummary(authorId, userLookup.dtoOf(authorId).displayName()), saved.getAnnouncementId(), margin.getId()));

        return saved;
    }

    public Announcement editAnnouncement(AnnouncementDTO announcementDTO) {
        Announcement announcement = getById(announcementDTO.announcementId());
        announcement.setTitle(announcementDTO.title());
        announcement.setContent(announcementDTO.content());
        announcement.setEditedAt(Instant.now());
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
