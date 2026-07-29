package org.margin.server.social.margin.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.shared.ratelimit.RateLimitConfig;
import org.margin.server.shared.ratelimit.RateLimitService;
import org.margin.server.shared.exceptions.TooManyRequestsException;
import org.margin.server.social.margin.models.dtos.*;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.models.Visibility;
import org.margin.server.users.models.User;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/margins")
public class MarginController {

    private final MarginService marginService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final RateLimitService rateLimitService;
    private final UserLookup userLookup;

    public MarginController(MarginService marginService,
                            MarginAuthorizationService marginAuthorizationService,
                            RateLimitService rateLimitService,
                              UserLookup userLookup) {
        this.marginService = marginService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.rateLimitService = rateLimitService;
        this.userLookup = userLookup;
    }

    @PostMapping(value = "/create_new_margin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MarginDTO> createNewMargin(
            @RequestPart("data") CreateNewMarginRequest request,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String key = "create_margin:" + user.id();
        if (!rateLimitService.tryConsume(key, RateLimitConfig.createMargin())) {
            throw new TooManyRequestsException("You can only create 5 margins per hour.");
        }

        MarginDTO created = marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()),
                marginIcon,
                entityOf(user));

        return ResponseEntity.ok(created);
    }

    @GetMapping("/get_margin/{marginId}")
    public MarginDTO getMargin(@PathVariable Long marginId,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        marginAuthorizationService.requireMarginMember(user.id(), marginId);
        return marginService.getMarginAsDto(marginId);
    }

    @GetMapping("/get_margins")
    public Set<MarginDTO> getMargins(@AuthenticationPrincipal AuthenticatedUser user) {
        return marginService.getMarginsForUser(entityOf(user));
    }

    @PostMapping("/update_margin")
    public ResponseEntity<MarginDTO> updateMargin(
            @RequestPart("data") UpdateMarginDTO updateMarginDTO,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (marginIcon != null && !marginIcon.isEmpty()) {
            String key = "update_margin_avatar:" + user.id();
            if (!rateLimitService.tryConsume(key, RateLimitConfig.uploadImage())) {
                throw new TooManyRequestsException("You can only update user avatar three times an hour.");
            }
        }
        marginAuthorizationService.requireMarginAdmin(user.id(), updateMarginDTO.marginId());
        MarginDTO updated = marginService.updateMarginAsDto(updateMarginDTO, marginIcon);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/update_member_role")
    public ResponseEntity<MarginMemberDTO> updateMarginMemberRole(
            @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        marginAuthorizationService.requireMarginAdmin(user.id(), request.marginId());
        return ResponseEntity.ok(
                marginService.updateMarginMemberRole(request.marginId(), user.id(), request.member())
        );
    }

    @PostMapping("/remove_margin_member")
    public ResponseEntity<Void> removeMarginMember(
            @RequestBody RemoveMarginMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!user.id().equals(request.userIdToRemove())) {
            marginAuthorizationService.requireMarginAdmin(user.id(), request.marginId());
        }
        
        marginService.removeMarginMember(request.marginId(), request.userIdToRemove());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete_margin")
    public ResponseEntity<Void> deleteMargin(@RequestBody Long marginId,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        marginAuthorizationService.requireMarginAdmin(user.id(), marginId);
        marginService.deleteMargin(marginId);
        return ResponseEntity.ok().build();
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userLookup.findById(principal.id()).orElseThrow();
    }
}
