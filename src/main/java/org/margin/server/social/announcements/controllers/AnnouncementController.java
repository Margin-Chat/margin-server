package org.margin.server.social.announcements.controllers;

import org.margin.server.social.announcements.models.dtos.AnnouncementDTO;
import org.margin.server.social.announcements.models.dtos.CreateAnnouncementRequest;
import org.margin.server.social.announcements.models.dtos.DeleteAnnouncementRequest;
import org.margin.server.social.announcements.services.AnnouncementService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService announcementService;
    private final MarginAuthorizationService marginAuthorizationService;

    public AnnouncementController(AnnouncementService announcementService,
                                  MarginAuthorizationService marginAuthorizationService) {
        this.announcementService = announcementService;
        this.marginAuthorizationService = marginAuthorizationService;
    }

    @GetMapping("/get/{marginId}")
    public List<AnnouncementDTO> getAnnouncements(@PathVariable Long marginId) {
        return announcementService.getAnnouncementsForMargin(marginId).stream()
                .map(announcementService::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/create")
    public AnnouncementDTO createAnnouncement(@RequestBody CreateAnnouncementRequest announcementDTO,
                                              @AuthenticationPrincipal User author) {
        marginAuthorizationService.requireMarginAdmin(author.getId(), announcementDTO.marginId());
        return announcementService.toDTO(announcementService.createAnnouncement(announcementDTO, author));
    }

    @PostMapping("/edit")
    public AnnouncementDTO editAnnouncement(@RequestBody AnnouncementDTO announcementDTO,
                                            @AuthenticationPrincipal User author) {
        marginAuthorizationService.requireMarginAdmin(author.getId(), announcementDTO.marginId());
        return announcementService.toDTO(announcementService.editAnnouncement(announcementDTO));
    }

    @PostMapping("/delete")
    public void deleteAnnouncement(@RequestBody DeleteAnnouncementRequest request,
                                   @AuthenticationPrincipal User author) {
        marginAuthorizationService.requireMarginAdmin(author.getId(), request.marginId());
        announcementService.deleteAnnouncement(request.announcementId());
    }
}
