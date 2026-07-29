package org.margin.server.social.margin.controllers;

import lombok.RequiredArgsConstructor;
import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginInvite;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.models.dtos.MarginInviteDTO;
import org.margin.server.social.margin.service.MarginInviteService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class MarginInviteController {

    private final MarginInviteService marginInviteService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final MarginService marginService;
    private final MarginMapper marginMapper;
    private final UserService userService;

    @PostMapping("/margins/{marginId}/link")
    public ResponseEntity<MarginInviteDTO> createLinkInvite(
            @PathVariable Long marginId,
            @RequestParam(required = false) Integer maxUses,
            @AuthenticationPrincipal AuthenticatedUser user) {

        marginAuthorizationService.requireMarginAdmin(user.id(), marginId);

        if (maxUses != null && maxUses <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite code can't be used more");
        }

        Margin margin = marginService.getById(marginId);
        MarginInvite invite = marginInviteService.createLinkInvite(margin, maxUses, user.id());

        return ResponseEntity.ok(toDTO(invite));
    }

    @PostMapping("/margins/{marginId}/email")
    public ResponseEntity<MarginInviteDTO> createDirectInvite(
            @PathVariable Long marginId,
            @RequestParam String email,
            @AuthenticationPrincipal AuthenticatedUser user) {

        marginAuthorizationService.requireMarginAdmin(user.id(), marginId);

        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is empty");
        }

        User targetUser = userService.getByEmail(email);

        if (marginService.isUserMember(marginId, targetUser)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already member of the margin");
        }

        if (marginInviteService.hasPendingInviteForMargin(marginId, targetUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a pending invite to this margin");
        }

        MarginInvite invite = marginInviteService.createDirectInvite(
                marginService.getById(marginId),
                targetUser.getId(),
                user.id()
        );

        return ResponseEntity.ok(toDTO(invite));
    }

    @GetMapping("/join/{code}")
    public ResponseEntity<MarginInviteDTO> getInviteDetails(@PathVariable String code) {

        MarginInvite invite = marginInviteService.findInviteDetails(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code not found"));

        if (!invite.isUsable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite is no longer valid");
        }

        return ResponseEntity.ok(toDTO(invite));
    }

    @PostMapping("/join/{code}")
    public ResponseEntity<MarginDTO> acceptLinkInvite(
            @PathVariable String code,
            @AuthenticationPrincipal AuthenticatedUser user) {

        MarginInvite invite = marginInviteService.findInviteDetails(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code not found"));

        if (!invite.isUsable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite is no longer valid");
        }

        if (marginService.isUserMember(invite.getMargin().getId(), entityOf(user))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already member of the margin");
        }

        Margin margin = marginInviteService.acceptLinkInvite(invite, entityOf(user));

        return ResponseEntity.ok(marginMapper.marginToDto(margin));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<MarginDTO> acceptDirectInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        MarginInvite invite = marginInviteService.getById(id);

        if (!invite.getInvitedUserId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invite is not for you");
        }

        if (!invite.isUsable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite is no longer valid");
        }

        Margin margin = marginInviteService.acceptDirectInvite(invite, entityOf(user));

        return ResponseEntity.ok(marginService.getMarginAsDto(margin.getId()));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> declineDirectInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        MarginInvite invite = marginInviteService.getById(id);

        if (!invite.getInvitedUserId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invite is not for you");
        }

        if (invite.getStatus() != MarginInvite.InviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite has already been resolved");
        }

        marginInviteService.declineDirectInvite(invite);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<List<MarginInviteDTO>> getPendingInvites(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(
                marginInviteService.getPendingInvites(user.id()).stream()
                        .map(this::toDTO)
                        .toList()
        );
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userService.getById(principal.id());
    }

    private MarginInviteDTO toDTO(MarginInvite invite) {
        return MarginInviteDTO.from(invite,
                nameOf(invite.getInvitedByUserId()),
                nameOf(invite.getInvitedUserId()));
    }

    private String nameOf(Long userId) {
        return userId == null ? null : userService.getById(userId).getDisplayName();
    }
}