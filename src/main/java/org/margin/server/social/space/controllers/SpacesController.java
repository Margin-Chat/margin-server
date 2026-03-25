package org.margin.server.social.space.controllers;

import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpacesController {

    private final SpacesService spacesService;
    private final ConnectionManager connectionManager;
    private final MarginAuthorizationService marginAuthorizationService;
    private final UserService userService;
    private final MarginService marginService;
    private final MarginMapper marginMapper;

    public SpacesController(SpacesService spacesService,
                            ConnectionManager connectionManager,
                            MarginAuthorizationService marginAuthorizationService,
                            UserService userService,
                            MarginService marginService,
                            MarginMapper marginMapper) {
        this.spacesService = spacesService;
        this.connectionManager = connectionManager;
        this.marginAuthorizationService = marginAuthorizationService;
        this.userService = userService;
        this.marginService = marginService;
        this.marginMapper = marginMapper;
    }

    @GetMapping("get_all_spaces_for_margin/{marginId}")
    public List<SpaceDTO> getAllSpaces(@PathVariable Long marginId, @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), marginId);
        return spacesService.getSpacesForMargin(marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();
    }

    @GetMapping("get_all_spaces_for_user/{marginId}")
    public List<SpaceDTO> getAllSpacesForUser(@AuthenticationPrincipal User user, @PathVariable Long marginId) {
        return spacesService.getSpacesForUserInMargin(user, marginId).stream()
                .map(marginMapper::spaceToDto)
                .toList();
    }

    @PostMapping("create_space")
    public ResponseEntity<SpaceDTO> createSpace(@AuthenticationPrincipal User user,
                                                @RequestBody CreateSpaceDTO dto) {
        marginAuthorizationService.requireMarginAdmin(user.getId(), dto.marginId());
        return ResponseEntity.ok(marginMapper.spaceToDto(spacesService.createNewSpace(
                dto,
                user,
                marginService.getById(dto.marginId()), false)));
    }

    @PostMapping("add_space_member")
    public ResponseEntity<SpaceMemberDTO> addSpaceMember(@AuthenticationPrincipal User user,
                                                         @RequestBody SpaceMemberDTO spaceMemberDTO) {
        Long marginId = spacesService.getById(spaceMemberDTO.spaceId()).getMargin().getId();
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceMemberDTO.spaceId(), marginId);

        User userToAdd = userService.getById(spaceMemberDTO.user().id());
        Space space = spacesService.getById(spaceMemberDTO.spaceId());

        SpaceMember member = spacesService.addNewUserToSpace(userToAdd, space, spaceMemberDTO.role());
        return ResponseEntity.ok(new SpaceMemberDTO(
                new UserDTO(member.getUser(), connectionManager.isUserOnline(member.getUser().getId())),
                member.getSpace().getId(),
                member.getRole(),
                member.getJoinedAt()
        ));
    }

    @PostMapping("update_space_info")
    public ResponseEntity<SpaceDTO> updateSpaceInfo(@AuthenticationPrincipal User user,
                                                    @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceDTO.spaceId(), spaceDTO.marginId());
        return ResponseEntity.ok(marginMapper.spaceToDto(spacesService.updateSpace(spaceDTO)));
    }

    @PostMapping("delete_space")
    public ResponseEntity<Void> deleteSpace(@AuthenticationPrincipal User user,
                                            @RequestBody SpaceDTO spaceDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), spaceDTO.spaceId(), spaceDTO.marginId());
        spacesService.deleteSpace(spaceDTO.spaceId());
        return ResponseEntity.ok().build();
    }
}