package org.margin.server.social.margin.controllers;

import org.margin.server.config.ratelimit.RateLimitConfig;
import org.margin.server.config.ratelimit.RateLimitService;
import org.margin.server.exceptions.TooManyRequestsException;
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

    public MarginController(MarginService marginService,
                            MarginAuthorizationService marginAuthorizationService,
                            RateLimitService rateLimitService) {
        this.marginService = marginService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(value = "/create_new_margin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MarginDTO> createNewMargin(
            @RequestPart("data") CreateNewMarginRequest request,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal User user) {
        String key = "create_margin:" + user.getId();
        if (!rateLimitService.tryConsume(key, RateLimitConfig.createMargin())) {
            throw new TooManyRequestsException("You can only create 5 margins per hour.");
        }

        MarginDTO created = marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()),
                marginIcon,
                user);

        return ResponseEntity.ok(created);
    }

    @GetMapping("/get_margin/{marginId}")
    public MarginDTO getMargin(@PathVariable Long marginId,
                               @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginMember(user.getId(), marginId);
        return marginService.getMarginAsDto(marginId);
    }

    @GetMapping("/get_margins")
    public Set<MarginDTO> getMargins(@AuthenticationPrincipal User user) {
        return marginService.getMarginsForUser(user);
    }

    @PostMapping("/update_margin")
    public ResponseEntity<MarginDTO> updateMargin(
            @RequestPart("data") UpdateMarginDTO updateMarginDTO,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal User user) {
        if (marginIcon != null && !marginIcon.isEmpty()) {
            String key = "update_margin_avatar:" + user.getId();
            if (!rateLimitService.tryConsume(key, RateLimitConfig.uploadImage())) {
                throw new TooManyRequestsException("You can only update user avatar three times an hour.");
            }
        }
        marginAuthorizationService.requireMarginAdmin(user.getId(), updateMarginDTO.marginId());
        MarginDTO updated = marginService.updateMarginAsDto(updateMarginDTO, marginIcon);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/update_member_role")
    public ResponseEntity<MarginMemberDTO> updateMarginMemberRole(
            @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal User user) {

        marginAuthorizationService.requireMarginAdmin(user.getId(), request.marginId());
        return ResponseEntity.ok(
                marginService.updateMarginMemberRole(request.marginId(), request.member())
        );
    }

    @PostMapping("/remove_margin_member")
    public ResponseEntity<Void> removeMarginMember(
            @RequestBody RemoveMarginMemberRequest request,
            @AuthenticationPrincipal User user) {

        marginAuthorizationService.requireMarginAdmin(user.getId(), request.marginId());
        marginService.removeMarginMember(request.marginId(), request.userIdToRemove());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete_margin")
    public ResponseEntity<Void> deleteMargin(@RequestBody Long marginId,
                                             @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), marginId);
        marginService.deleteMargin(marginId);
        return ResponseEntity.ok().build();
    }
}
