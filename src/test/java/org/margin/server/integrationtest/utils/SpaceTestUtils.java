package org.margin.server.integrationtest.utils;

import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.controllers.SpacesController;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.CreateSpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SpaceTestUtils {

    private static SpacesController spacesController;
    private static SpacesService spacesService;
    private static ConnectionManager connectionManager;

    @Autowired
    public SpaceTestUtils(SpacesController spacesController,
                          SpacesService spacesService,
                          ConnectionManager connectionManager) {
        SpaceTestUtils.spacesController = spacesController;
        SpaceTestUtils.spacesService = spacesService;
        SpaceTestUtils.connectionManager = connectionManager;
    }

    public static SpaceDTO createSpace(String name, Long marginId, User user) {
        var dto = new CreateSpaceDTO(name, "Test space", Visibility.PUBLIC, marginId);
        return spacesController.createSpace(user, dto).getBody();
    }

    public static SpaceMemberDTO addMember(Long spaceId, User userToAdd, SpaceRole role, User actingUser) {
        var memberDto = new SpaceMemberDTO(
                new UserDTO(userToAdd, connectionManager.isUserOnline(userToAdd.getId())),
                spaceId,
                role,
                Instant.now()
        );
        return spacesController.addSpaceMember(actingUser, memberDto).getBody();
    }

    public static List<SpaceDTO> getSpacesForMargin(Long marginId, User user) {
        return spacesController.getAllSpaces(marginId, user);
    }

    public static List<SpaceDTO> getSpacesForUser(Long marginId, User user) {
        return spacesController.getAllSpacesForUser(user, marginId);
    }

    public static SpaceDTO updateSpace(Long spaceId, String name, String description, User user) {
        var dto = new SpaceDTO(spaceId, name, description, null, null, null, null, false);
        return spacesController.updateSpaceInfo(user, dto).getBody();
    }

    public static void deleteSpace(Long spaceId, User user) {
        var dto = new SpaceDTO(spaceId, null, null, null, null, null, null, false);
        spacesController.deleteSpace(user, dto);
    }

    public static void removeMember(Long spaceId, User userToRemove, User actingUser) {
        var memberDto = new SpaceMemberDTO(
                new UserDTO(userToRemove, false),
                spaceId,
                null,
                null
        );
        spacesController.removeSpaceMember(memberDto, actingUser);
    }

    public static SpaceMemberDTO updateMemberRole(Long spaceId, User targetUser, SpaceRole role, User actingUser) {
        var memberDto = new SpaceMemberDTO(
                new UserDTO(targetUser, false),
                spaceId,
                role,
                null
        );
        return spacesController.updateSpaceMemberRole(actingUser, memberDto).getBody();
    }

    public static Space getById(Long spaceId) {
        return spacesService.getById(spaceId);
    }
}