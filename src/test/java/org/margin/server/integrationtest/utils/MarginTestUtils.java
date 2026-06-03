package org.margin.server.integrationtest.utils;

import org.junit.jupiter.api.Assertions;
import org.margin.server.social.margin.controllers.MarginController;
import org.margin.server.social.margin.controllers.MarginInviteController;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.*;
import org.margin.server.social.margin.repositories.MarginMemberRepository;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MarginTestUtils {
    private static MarginController marginController;
    private static MarginInviteController marginInviteController;
    private static MarginService marginService;
    private static MarginMemberRepository marginMemberRepository;

    @Autowired
    public MarginTestUtils(MarginController marginController,
                           MarginInviteController marginInviteController,
                           MarginService marginService,
                           MarginMemberRepository marginMemberRepository) {
        MarginTestUtils.marginController = marginController;
        MarginTestUtils.marginInviteController = marginInviteController;
        MarginTestUtils.marginService = marginService;
        MarginTestUtils.marginMemberRepository = marginMemberRepository;
    }

    public static Margin createMargin(String marginName, User user) {
        var request = new CreateNewMarginRequest(marginName, "test", "PUBLIC");
        MarginDTO dto = marginController.createNewMargin(request, null, user).getBody();
        Assertions.assertNotNull(dto, "Margin was successfully created");
        return marginService.getById(dto.marginId());
    }

    public static void addUserToMargin(Long marginId, User addingUser, User userToAdd) {
        MarginInviteDTO invite =
                marginInviteController.createDirectInvite(marginId, userToAdd.getEmail(), addingUser).getBody();
        Assertions.assertNotNull(invite, "Invite was successfully created");
        marginInviteController.acceptDirectInvite(invite.id(), userToAdd);

        Set<MarginDTO> margins = marginController.getMargins(userToAdd);
        Assertions.assertNotNull(
                margins.stream()
                        .map(MarginDTO::marginId)
                        .filter(id -> id.equals(marginId))
                        .collect(Collectors.toSet()),
                "Margin member was successfully added");
    }

    public static List<MarginMember> getMembersFromMargin(Long marginId) {
        return marginMemberRepository.findByMargin_Id(marginId);
    }

    public static void removeUserFromMargin(Long marginId, User removingUser, User userToRemove) {
        Optional<MarginMember> marginMember =
                marginMemberRepository.findByUser_IdAndMargin_Id(userToRemove.getId(), marginId);
        Assertions.assertTrue(marginMember.isPresent());
        var request = new RemoveMarginMemberRequest(marginId, marginMember.get().getUser().getId());
        marginController.removeMarginMember(request, removingUser);
    }

    public static Margin getMargin(Long marginId, User user) {
        return marginService.getById(marginController.getMargin(marginId, user).marginId());
    }

    public static void updateMargin(Long marginId, String name, String description, User user) {
        var request = new UpdateMarginDTO(marginId, name, description);
        marginController.updateMargin(request, null, user);
    }

    public static void deleteMargin(Long marginId, User user) {
        marginController.deleteMargin(marginId, user);
    }

    public static void updateMemberRole(Long marginId, User targetUser, MarginRole newRole, User actingUser) {
        var memberDto = new MarginMemberDTO(
                new UserDTO(targetUser, false),
                newRole,
                Instant.now()
        );
        var request = new UpdateMemberRoleRequest(marginId, memberDto);
        marginController.updateMarginMemberRole(request, actingUser);
    }

    public static MarginDTO getMarginDto(Long marginId, User user) {
        return marginController.getMargin(marginId, user);
    }
}
