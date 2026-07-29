package org.margin.server.social.space.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpacesController {

    private final SpacesService spacesService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final UserService userService;
    private final MarginService marginService;
    private final UserLookup userLookup;

    public SpacesController(SpacesService spacesService,
                            MarginAuthorizationService marginAuthorizationService,
                            UserService userService,
                            MarginService marginService,
                              UserLookup userLookup) {
        this.spacesService = spacesService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.userService = userService;
        this.marginService = marginService;
        this.userLookup = userLookup;
    }

    @GetMapping("get_all_spaces_for_margin/{marginId}")
    public List<SpaceDTO> getAllSpaces(@PathVariable Long marginId,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        marginAuthorizationService.requireMarginAdmin(user.id(), marginId);
        return spacesService.getSpacesForMargin(marginId);
    }

    @GetMapping("get_all_spaces_for_user/{marginId}")
    public List<SpaceDTO> getAllSpacesForUser(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable Long marginId) {
        return spacesService.getSpacesForUserInMargin(user.id(), marginId);
    }

    @PostMapping("create_space")
    public ResponseEntity<SpaceDTO> createSpace(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestBody CreateSpaceDTO dto) {
        marginAuthorizationService.requireMarginAdmin(user.id(), dto.marginId());
        Margin margin = marginService.getById(dto.marginId());
        SpaceDTO created = spacesService.createNewSpace(dto, user.id(), margin);
        return ResponseEntity.ok(created);
    }

    @PostMapping("add_space_member")
    public ResponseEntity<SpaceMemberDTO> addSpaceMember(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestBody SpaceMemberDTO spaceMemberDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), spaceMemberDTO.spaceId());

        User userToAdd = userService.getById(spaceMemberDTO.user().id());
        SpaceRole role = spaceMemberDTO.role() == null
                ? SpaceRole.MEMBER
                : SpaceRole.valueOf(spaceMemberDTO.role().name());

        SpaceMemberDTO memberDto = spacesService.addNewUserToSpace(userToAdd.getId(), spaceMemberDTO.spaceId(), role);
        return ResponseEntity.ok(memberDto);
    }

    @PostMapping("update_space_info")
    public ResponseEntity<SpaceDTO> updateSpaceInfo(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), spaceDTO.spaceId());
        SpaceDTO updated = spacesService.updateSpace(spaceDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("delete_space")
    public ResponseEntity<Void> deleteSpace(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), spaceDTO.spaceId());
        spacesService.deleteSpace(spaceDTO.spaceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("update_space_member_role")
    public ResponseEntity<SpaceMemberDTO> updateSpaceMemberRole(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @RequestBody SpaceMemberDTO spaceMemberDTO) {
        Space space = spacesService.getById(spaceMemberDTO.spaceId());
        marginAuthorizationService.requireSpaceAdmin(user.id(), space.getId());
        SpaceMemberDTO result = spacesService.updateSpaceMemberRole(space, spaceMemberDTO);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("remove_space_member")
    public ResponseEntity<Void> removeSpaceMember(@RequestBody SpaceMemberDTO spaceMemberDTO,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), spaceMemberDTO.spaceId());
        spacesService.removeUserFromSpaces(
                spaceMemberDTO.user().id(),
                Collections.singletonList(spaceMemberDTO.spaceId())
        );
        return ResponseEntity.ok().build();
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userLookup.findById(principal.id()).orElseThrow();
    }
}
