package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.users.models.User;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarginTest extends MarginTestRunner {
    private User admin;
    private User member;
    private Long marginId;

    @BeforeEach
    void setUp() {
        admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        member = UserTestUtils.createUser("member", "member@margin.chat");
        Margin margin = MarginTestUtils.createMargin("TestMargin", admin);
        marginId = margin.getId();
    }

    @Test
    void addUserToMargin() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        List<MarginMember> members = MarginTestUtils.getMembersFromMargin(marginId);
        assertEquals(2, members.size());
    }

    @Test
    void removeUserFromMargin() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);
        assertEquals(2, MarginTestUtils.getMembersFromMargin(marginId).size());

        MarginTestUtils.removeUserFromMargin(marginId, admin, member);
        assertEquals(1, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void nonAdminCannotAddMember() {
        User newUser = UserTestUtils.createUser("newuser", "new@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.addUserToMargin(marginId, member, newUser)
        );

        assertEquals(2, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void nonAdminCannotRemoveMember() {
        User otherMember = UserTestUtils.createUser("other", "other@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, member);
        MarginTestUtils.addUserToMargin(marginId, admin, otherMember);

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.removeUserFromMargin(marginId, member, otherMember)
        );

        assertEquals(3, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void nonMemberCannotAccessMargin() {
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.getMargin(marginId, outsider)
        );
    }

    @Test
    void memberCanAccessMargin() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        assertDoesNotThrow(() -> MarginTestUtils.getMargin(marginId, member));
    }

    @Test
    void nonAdminCannotUpdateMargin() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.updateMargin(marginId, "New Name", "New Desc", member)
        );
    }

    @Test
    void adminCanUpdateMargin() {
        MarginTestUtils.updateMargin(marginId, "Updated", "Updated Desc", admin);

        Margin updated = MarginTestUtils.getMargin(marginId, admin);
        assertEquals("Updated", updated.getName());
        assertEquals("Updated Desc", updated.getDescription());
    }

    @Test
    void nonAdminCannotDeleteMargin() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.deleteMargin(marginId, member)
        );

        assertDoesNotThrow(() -> MarginTestUtils.getMargin(marginId, admin));
    }

    @Test
    void adminCanDeleteMargin() {
        MarginTestUtils.deleteMargin(marginId, admin);

        assertThrows(Exception.class, () ->
                MarginTestUtils.getMargin(marginId, admin)
        );
    }

    @Test
    void cannotRemoveLastAdmin() {
        assertThrows(RuntimeException.class, () ->
                MarginTestUtils.removeUserFromMargin(marginId, admin, admin)
        );

        assertEquals(1, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void canRemoveAdminWhenAnotherAdminExists() {
        User secondAdmin = UserTestUtils.createUser("admin2", "admin2@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, secondAdmin);
        MarginTestUtils.updateMemberRole(marginId, secondAdmin, MarginRole.ADMIN, admin);
        MarginTestUtils.removeUserFromMargin(marginId, secondAdmin, admin);

        assertEquals(1, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void nonAdminCannotChangeRoles() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.updateMemberRole(marginId, member, MarginRole.ADMIN, member)
        );
    }

    @Test
    void adminCanChangeRoles() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);

        MarginTestUtils.updateMemberRole(marginId, member, MarginRole.ADMIN, admin);

        MarginMember updated = MarginTestUtils.getMembersFromMargin(marginId).stream()
                .filter(m -> m.getUser().getId().equals(member.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(MarginRole.ADMIN, updated.getRole());
    }

    @Test
    void cannotDemoteLastAdmin() {
        assertThrows(RuntimeException.class, () ->
                MarginTestUtils.updateMemberRole(marginId, admin, MarginRole.MEMBER, admin)
        );
    }

    @Test
    void addingDuplicateUserDoesNotCreateSecondMember() {
        MarginTestUtils.addUserToMargin(marginId, admin, member);
        assertThrows(ResponseStatusException.class, () ->
                MarginTestUtils.addUserToMargin(marginId, admin, member));

        assertEquals(2, MarginTestUtils.getMembersFromMargin(marginId).size());
    }

    @Test
    void creatingMarginAutoAddsCreatorAsAdmin() {
        List<MarginMember> members = MarginTestUtils.getMembersFromMargin(marginId);

        assertEquals(1, members.size());
        assertEquals(admin.getId(), members.getFirst().getUser().getId());
        assertEquals(MarginRole.ADMIN, members.getFirst().getRole());
    }

    @Test
    void creatingMarginCreatesDefaultSpace() {
        MarginDTO dto = MarginTestUtils.getMarginDto(marginId, admin);

        assertFalse(dto.spaces().isEmpty());
        assertEquals("General Space", dto.spaces().getFirst().spaceName());
    }
}