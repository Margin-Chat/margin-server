package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SpaceTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.users.models.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpaceTest extends MarginTestRunner {
    private User admin;
    private User member;
    private Long marginId;

    @BeforeEach
    void setUp() {
        admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        member = UserTestUtils.createUser("member", "member@margin.chat");
        Margin margin = MarginTestUtils.createMargin("TestMargin", admin);
        marginId = margin.getId();
        MarginTestUtils.addUserToMargin(margin.getId(), admin, member);
    }

    @Test
    void createSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("New Space", marginId, admin);

        assertNotNull(space);
        assertEquals("New Space", space.spaceName());
    }

    @Test
    void createSpaceWithDuplicateName_throws() {
        SpaceTestUtils.createSpace("Unique Space", marginId, admin);

        assertThrows(DuplicateKeyException.class, () ->
                SpaceTestUtils.createSpace("Unique Space", marginId, admin)
        );
    }

    @Test
    void nonAdminCannotCreateSpace() {
        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.createSpace("Forbidden Space", marginId, member)
        );
    }

    @Test
    void adminCanAddMemberToSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("Members Space", marginId, admin);

        SpaceMemberDTO added = SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);

        assertNotNull(added);
        assertEquals(member.getId(), added.user().id());
        assertEquals(SpaceRole.MEMBER, added.role());
    }

    @Test
    void nonAdminCannotAddMember() {
        SpaceDTO space = SpaceTestUtils.createSpace("Restricted Space", marginId, admin);
        User newUser = UserTestUtils.createUser("newuser", "new@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, newUser);
        Long spaceId = space.spaceId();
        SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin);

        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.addMember(spaceId, newUser, SpaceRole.MEMBER, member)
        );
    }

    @Test
    void cannotAddDuplicateMember() {
        SpaceDTO space = SpaceTestUtils.createSpace("No Dupes", marginId, admin);
        Long spaceId = space.spaceId();
        SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin);

        assertThrows(DuplicateKeyException.class, () ->
                SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin)
        );
    }

    @Test
    void cannotAddNonMarginMemberToSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("Margin Only", marginId, admin);
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        assertThrows(Exception.class, () ->
                SpaceTestUtils.addMember(space.spaceId(), outsider, SpaceRole.MEMBER, admin)
        );
    }

    @Test
    void adminCanUpdateSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("Old Name", marginId, admin);

        SpaceDTO updated = SpaceTestUtils.updateSpace(space.spaceId(), "New Name", "New Desc", admin);

        assertEquals("New Name", updated.spaceName());
        assertEquals("New Desc", updated.spaceDescription());
    }

    @Test
    void nonAdminCannotUpdateSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("Protected", marginId, admin);
        Long spaceId = space.spaceId();
        SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin);

        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.updateSpace(spaceId, "Hacked", "Hacked Desc", member)
        );
    }

    @Test
    void adminCanDeleteSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("To Delete", marginId, admin);
        Long spaceId = space.spaceId();

        SpaceTestUtils.deleteSpace(spaceId, admin);

        assertThrows(Exception.class, () -> SpaceTestUtils.getById(spaceId));
    }

    @Test
    void nonAdminCannotDeleteSpace() {
        SpaceDTO space = SpaceTestUtils.createSpace("Protected", marginId, admin);
        SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);

        Long spaceId = space.spaceId();
        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.deleteSpace(spaceId, member)
        );
    }

    @Test
    void adminCanRemoveMember() {
        SpaceDTO space = SpaceTestUtils.createSpace("Removal Test", marginId, admin);
        SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);

        SpaceTestUtils.removeMember(space.spaceId(), member, admin);

        List<SpaceDTO> userSpaces = SpaceTestUtils.getSpacesForUser(marginId, member);
        boolean stillInSpace = userSpaces.stream()
                .anyMatch(s -> s.spaceId().equals(space.spaceId()));
        assertFalse(stillInSpace);
    }

    @Test
    void nonAdminCannotRemoveMember() {
        SpaceDTO space = SpaceTestUtils.createSpace("No Remove", marginId, admin);
        User otherMember = UserTestUtils.createUser("other", "other@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, otherMember);
        Long spaceId = space.spaceId();
        SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin);
        SpaceTestUtils.addMember(spaceId, otherMember, SpaceRole.MEMBER, admin);

        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.removeMember(spaceId, otherMember, member)
        );
    }

    @Test
    void adminCanUpdateMemberRole() {
        SpaceDTO space = SpaceTestUtils.createSpace("Role Test", marginId, admin);
        SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);

        SpaceMemberDTO updated = SpaceTestUtils.updateMemberRole(
                space.spaceId(), member, SpaceRole.ADMIN, admin);

        assertEquals(SpaceRole.ADMIN, updated.role());
    }

    @Test
    void nonAdminCannotUpdateMemberRole() {
        SpaceDTO space = SpaceTestUtils.createSpace("Role Guard", marginId, admin);
        User otherMember = UserTestUtils.createUser("other", "other@margin.chat");
        MarginTestUtils.addUserToMargin(marginId, admin, otherMember);
        Long spaceId = space.spaceId();
        SpaceTestUtils.addMember(spaceId, member, SpaceRole.MEMBER, admin);
        SpaceTestUtils.addMember(spaceId, otherMember, SpaceRole.MEMBER, admin);

        assertThrows(ResponseStatusException.class, () ->
                SpaceTestUtils.updateMemberRole(spaceId, otherMember, SpaceRole.ADMIN, member)
        );
    }

    @Test
    void getSpacesForUser_onlyReturnsUserSpaces() {
        SpaceTestUtils.createSpace("Admin Only", marginId, admin);

        List<SpaceDTO> memberSpaces = SpaceTestUtils.getSpacesForUser(marginId, member);

        boolean hasAdminOnly = memberSpaces.stream()
                .anyMatch(s -> s.spaceName().equals("Admin Only"));
        assertFalse(hasAdminOnly);
    }

    @Test
    void marginCreationCreatesDefaultSpace() {
        List<SpaceDTO> spaces = SpaceTestUtils.getSpacesForMargin(marginId, admin);

        assertFalse(spaces.isEmpty());
        assertEquals("General Space", spaces.getFirst().spaceName());
    }

    @Test
    void newMarginMemberGetsAddedToDefaultSpaces() {
        List<SpaceDTO> memberSpaces = SpaceTestUtils.getSpacesForUser(marginId, member);

        boolean inDefault = memberSpaces.stream()
                .anyMatch(s -> s.spaceName().equals("General Space"));
        assertTrue(inDefault);
    }
}