package org.margin.server.social.space.controllers;

import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.margin.server.social.space.services.SpacesService;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpacesController {
    private final SpacesService spacesService;
    private final ConnectionManager connectionManager;

    public SpacesController(SpacesService spacesService,
                            ConnectionManager connectionManager) {
        this.spacesService = spacesService;
        this.connectionManager = connectionManager;
    }

    @GetMapping("get_all_spaces")
    public List<SpaceDTO> getAllSpaces(@AuthenticationPrincipal User user) {
        return spacesService.getSpaces().stream()
                .map(spacesService::toDTO)
                .toList();
    }

    @GetMapping("get_all_spaces_for_user")
    public List<SpaceDTO> getAllSpacesForUser(@AuthenticationPrincipal User user) {
        return spacesService.getSpacesForUser(user).stream()
                .map(spacesService::toDTO)
                .toList();
    }

    @PostMapping("create_space")
    public ResponseEntity<SpaceDTO> createSpace(@AuthenticationPrincipal User user,
                                                @RequestBody CreateSpaceDTO dto) {
        return ResponseEntity.ok(spacesService.toDTO(spacesService.createNewSpace(dto)));
    }

    @PostMapping("add_space_member")
    public ResponseEntity<SpaceMemberDTO> addSpaceMember(@AuthenticationPrincipal User user,
                                                         @RequestBody SpaceMemberDTO spaceMemberDTO) {
        SpaceMember member = spacesService.addNewSpaceMemberToSpace(spaceMemberDTO);
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
        return ResponseEntity.ok(spacesService.toDTO(spacesService.updateSpace(spaceDTO)));
    }

    @PostMapping("delete_space")
    public ResponseEntity<Void> deleteSpace(@AuthenticationPrincipal User user,
                                            @RequestBody Long spaceId) {
        spacesService.deleteSpace(spaceId);
        return ResponseEntity.ok().build();
    }
}
