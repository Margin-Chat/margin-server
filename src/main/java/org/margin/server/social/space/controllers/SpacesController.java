package org.margin.server.social.space.controllers;

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

    public SpacesController(SpacesService spacesService,
                            MarginAuthorizationService marginAuthorizationService,
                            UserService userService,
                            MarginService marginService) {
        this.spacesService = spacesService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.userService = userService;
        this.marginService = marginService;
    }

    @GetMapping("get_all_spaces_for_margin/{marginId}")
    public List<SpaceDTO> getAllSpaces(@PathVariable Long marginId,
                                       @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), marginId);
        return spacesService.getSpacesForMargin(marginId);
    }

    @GetMapping("get_all_spaces_for_user/{marginId}")
    public List<SpaceDTO> getAllSpacesForUser(@AuthenticationPrincipal User user,
                                              @PathVariable Long marginId) {
        return spacesService.getSpacesForUserInMargin(user, marginId);
    }

    @PostMapping("create_space")
    public ResponseEntity<SpaceDTO> createSpace(@AuthenticationPrincipal User user,
                                                @RequestBody CreateSpaceDTO dto) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), dto.marginId());
        Margin margin = marginService.getById(dto.marginId());
        SpaceDTO created = spacesService.createNewSpace(dto, user, margin, false);
        return ResponseEntity.ok(created);
    }

    @PostMapping("add_space_member")
    public ResponseEntity<SpaceMemberDTO> addSpaceMember(@AuthenticationPrincipal User user,
                                                         @RequestBody SpaceMemberDTO spaceMemberDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceMemberDTO.spaceId());

        User userToAdd = userService.getById(spaceMemberDTO.user().id());
        SpaceRole role = spaceMemberDTO.role() == null
                ? SpaceRole.MEMBER
                : SpaceRole.valueOf(spaceMemberDTO.role().name());

        SpaceMemberDTO memberDto = spacesService.addNewUserToSpace(userToAdd, spaceMemberDTO.spaceId(), role);
        return ResponseEntity.ok(memberDto);
    }

    @PostMapping("update_space_info")
    public ResponseEntity<SpaceDTO> updateSpaceInfo(@AuthenticationPrincipal User user,
                                                    @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceDTO.spaceId());
        SpaceDTO updated = spacesService.updateSpace(spaceDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("delete_space")
    public ResponseEntity<Void> deleteSpace(@AuthenticationPrincipal User user,
                                            @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceDTO.spaceId());
        spacesService.deleteSpace(spaceDTO.spaceId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("update_space_member_role")
    public ResponseEntity<SpaceMemberDTO> updateSpaceMemberRole(@AuthenticationPrincipal User user,
                                                                @RequestBody SpaceMemberDTO spaceMemberDTO) {
        Space space = spacesService.getById(spaceMemberDTO.spaceId());
        marginAuthorizationService.requireSpaceAdmin(user.getId(), space.getId());
        SpaceMemberDTO result = spacesService.updateSpaceMemberRole(space, spaceMemberDTO);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("remove_space_member")
    public ResponseEntity<Void> removeSpaceMember(@RequestBody SpaceMemberDTO spaceMemberDTO,
                                                  @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceMemberDTO.spaceId());
        spacesService.removeUserFromSpaces(
                spaceMemberDTO.user().id(),
                Collections.singletonList(spaceMemberDTO.spaceId())
        );
        return ResponseEntity.ok().build();
    }
}
