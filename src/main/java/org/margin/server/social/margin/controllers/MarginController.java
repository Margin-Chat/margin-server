package org.margin.server.social.margin.controllers;

import org.margin.server.social.margin.models.Margin;
import org.margin.server.social.margin.models.dtos.*;
import org.margin.server.social.margin.service.MarginMapper;
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
    private final MarginAuthorizationService authorizationService;
    private final MarginMapper marginMapper;

    public MarginController(MarginService marginService,
                            MarginAuthorizationService authorizationService, MarginMapper marginMapper) {
        this.marginService = marginService;
        this.authorizationService = authorizationService;
        this.marginMapper = marginMapper;
    }

    @PostMapping(value = "/create_new_margin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MarginDTO> createNewMargin(
            @RequestPart("data") CreateNewMarginRequest request,
            @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
            @AuthenticationPrincipal User user) {

        Margin created = marginService.createMargin(
                request.marginName(),
                request.marginDescription(),
                Visibility.valueOf(request.visibility()),
                marginIcon,
                user);

        return ResponseEntity.ok(marginMapper.marginToDto(created));
    }

    @GetMapping("/get_margin/{marginId}")
    public MarginDTO getMargin(@PathVariable Long marginId,
                               @AuthenticationPrincipal User user) {
        authorizationService.requireMarginMember(user.getId(), marginId);
        return marginMapper.marginToDto(marginService.getById(marginId));
    }

    @GetMapping("/get_margins")
    public Set<MarginDTO> getMargins(@AuthenticationPrincipal User user) {
        return marginService.getMarginsForUser(user);
    }

    @PostMapping("/update_margin")
    public ResponseEntity<MarginDTO> updateMargin(@RequestPart("data") UpdateMarginDTO updateMarginDTO,
                                                  @RequestPart(value = "marginIcon", required = false) MultipartFile marginIcon,
                                                  @AuthenticationPrincipal User user) {
        authorizationService.requireMarginAdmin(user.getId(), updateMarginDTO.marginId());
        return ResponseEntity.ok(marginMapper.marginToDto(marginService.updateMargin(updateMarginDTO, marginIcon)));
    }

    @PostMapping("/delete_margin")
    public ResponseEntity<Void> deleteMargin(@RequestBody Long marginId,
                                             @AuthenticationPrincipal User user) {
        authorizationService.requireMarginAdmin(user.getId(), marginId);
        marginService.deleteMargin(marginId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/add_margin_member")
    public ResponseEntity<MarginMemberDTO> addMarginMember(
            @RequestBody AddMarginMemberRequest request,
            @AuthenticationPrincipal User user) {
        authorizationService.requireMarginAdmin(user.getId(), request.marginId());
        return ResponseEntity.ok(marginService.memberToDto(
                marginService.addUserToMargin(request.marginId(), request.userId(), request.role())
        ));
    }

    @PostMapping("/update_member_role")
    public ResponseEntity<MarginMemberDTO> updateMarginMemberRole(
            @RequestBody UpdateMemberRoleRequest request,
            @AuthenticationPrincipal User user) {
        authorizationService.requireMarginAdmin(user.getId(), request.marginId());
        return ResponseEntity.ok(marginService.updateMarginMemberRole(request.marginId(), request.member()));
    }

    @PostMapping("/remove_margin_member")
    public ResponseEntity<Void> removeMarginMember(@RequestBody Long marginId,
                                                   @AuthenticationPrincipal User user) {
        authorizationService.requireMarginAdmin(user.getId(), marginId);
        marginService.removeMarginMember(marginId, user.getId());
        return ResponseEntity.ok().build();
    }
}
