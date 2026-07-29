package org.margin.server.social.announcements.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.announcements.models.dtos.AnnouncementDTO;
import org.margin.server.social.announcements.models.dtos.CreateAnnouncementRequest;
import org.margin.server.social.announcements.models.dtos.DeleteAnnouncementRequest;
import org.margin.server.social.announcements.services.AnnouncementService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService announcementService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final UserLookup userLookup;

    public AnnouncementController(AnnouncementService announcementService,
                                  MarginAuthorizationService marginAuthorizationService,
                              UserLookup userLookup) {
        this.announcementService = announcementService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.userLookup = userLookup;
    }

    @GetMapping("/get/{marginId}")
    public List<AnnouncementDTO> getAnnouncements(@PathVariable Long marginId,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        marginAuthorizationService.requireMarginMember(user.id(), marginId);
        return announcementService.getAnnouncementsForMargin(marginId).stream()
                .map(announcementService::toDTO)
                .toList();
    }

    @PostMapping("/create")
    public AnnouncementDTO createAnnouncement(@RequestBody CreateAnnouncementRequest announcementDTO,
                                              @AuthenticationPrincipal AuthenticatedUser author) {
        marginAuthorizationService.requireMarginAdmin(author.id(), announcementDTO.marginId());
        return announcementService.toDTO(announcementService.createAnnouncement(announcementDTO, author.id()));
    }

    @PostMapping("/edit")
    public AnnouncementDTO editAnnouncement(@RequestBody AnnouncementDTO announcementDTO,
                                            @AuthenticationPrincipal AuthenticatedUser author) {
        marginAuthorizationService.requireMarginAdmin(author.id(), announcementDTO.marginId());
        return announcementService.toDTO(announcementService.editAnnouncement(announcementDTO));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteAnnouncement(@RequestBody DeleteAnnouncementRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser author) {
        marginAuthorizationService.requireMarginAdmin(author.id(), request.marginId());
        announcementService.deleteAnnouncement(request.announcementId());
        return ResponseEntity.ok().build();
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userLookup.findById(principal.id()).orElseThrow();
    }
}
