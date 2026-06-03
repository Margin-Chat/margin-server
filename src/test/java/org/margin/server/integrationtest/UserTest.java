package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ChannelTestUtils;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SpaceTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.CurrentUserDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserTest extends MarginTestRunner {

    private User user;

    @BeforeEach
    void setUp() {
        user = UserTestUtils.createUser("lucas", "lucas@margin.chat");
    }

    @Test
    void getCurrentUser_returnsAuthenticatedUser() {
        CurrentUserDTO dto = UserTestUtils.getCurrentUser(user);

        assertEquals(user.getId(), dto.id());
        assertEquals("lucas", dto.displayName());
        assertEquals("lucas@margin.chat", dto.email());
    }

    @Test
    void lookupByEmail_returnsUser() {
        ResponseEntity<UserDTO> response = UserTestUtils.lookupByEmail(user, "lucas@margin.chat");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("lucas", response.getBody().displayName());
    }

    @Test
    void lookupByEmail_nonExistentUser_throws() {
        assertThrows(Exception.class, () ->
                UserTestUtils.lookupByEmail(user, "nonexistent@margin.chat")
        );
    }

    @Test
    void updateDisplayName() {
        CurrentUserDTO updated = UserTestUtils.updateUser("New Name", null, user);

        assertEquals("New Name", updated.displayName());
        assertEquals("lucas@margin.chat", updated.email());
    }

    @Test
    void updateEmail() {
        CurrentUserDTO updated = UserTestUtils.updateUser(null, "newemail@margin.chat", user);

        assertEquals("newemail@margin.chat", updated.email());
        assertEquals("lucas", updated.displayName());
    }

    @Test
    void updateDisplayNameAndEmail() {
        CurrentUserDTO updated = UserTestUtils.updateUser("Updated", "updated@margin.chat", user);

        assertEquals("Updated", updated.displayName());
        assertEquals("updated@margin.chat", updated.email());
    }

    @Test
    void updateWithNullsChangesNothing() {
        CurrentUserDTO updated = UserTestUtils.updateUser(null, null, user);

        assertEquals("lucas", updated.displayName());
        assertEquals("lucas@margin.chat", updated.email());
    }

    @Test
    void deleteUser_softDeletes() {
        Long userId = user.getId();

        UserTestUtils.deleteUser(user);

        User deleted = UserTestUtils.findById(userId);
        assertNotNull(deleted.getDeletedAt());
        assertEquals("Deleted User", deleted.getDisplayName());
        assertTrue(deleted.getEmail().startsWith("deleted_user_"));
        assertNull(deleted.getProfilePictureUrl());
    }

    @Test
    void deleteUser_clearsEncryptionKeys() {
        Long userId = user.getId();

        UserTestUtils.deleteUser(user);

        User deleted = UserTestUtils.findById(userId);
        assertNull(deleted.getEncryption().getPublicKey());
        assertNull(deleted.getEncryption().getEncryptedPrivateKey());
        assertNull(deleted.getEncryption().getIv());
        assertNull(deleted.getEncryption().getSalt());
    }

    @Test
    void deletedUserCannotBeLookedUp() {
        UserTestUtils.deleteUser(user);

        assertThrows(Exception.class, () ->
                UserTestUtils.lookupByEmail(user, "lucas@margin.chat")
        );
    }

    @Test
    void uploadKeys_forbiddenForDifferentUser() {
        User otherUser = UserTestUtils.createUser("other", "other@margin.chat");

        ResponseEntity<Void> response = UserTestUtils.uploadKeys(user.getId(), "pub", "priv", otherUser);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void uploadKeys_allowedForSameUser() {
        ResponseEntity<Void> response = UserTestUtils.uploadKeys(user.getId(), "pub-key", "enc-priv-key", user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteUser_removesFromAllMarginsSpacesAndChannels() {
        User admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        Margin margin = MarginTestUtils.createMargin("TestMargin", admin);
        MarginTestUtils.addUserToMargin(margin.getId(), admin, user);

        SpaceDTO space = SpaceTestUtils.createPrivateSpace("TestSpace", margin.getId(), admin);
        SpaceTestUtils.addMember(space.spaceId(), user, SpaceRole.MEMBER, admin);

        ChannelTestUtils.createChannel(space.spaceId(), "TestChannel", admin);

        Long userId = user.getId();
        Long marginId = margin.getId();
        Long spaceId = space.spaceId();

        UserTestUtils.deleteUser(user);

        List<MarginMember> marginMembers = MarginTestUtils.getMembersFromMargin(marginId);
        boolean inMargin = marginMembers.stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        assertFalse(inMargin);

        List<SpaceDTO> userSpaces = SpaceTestUtils.getSpacesForUser(marginId, UserTestUtils.findById(userId));
        boolean inSpace = userSpaces.stream()
                .anyMatch(s -> s.spaceId().equals(spaceId));
        assertFalse(inSpace);

        User deleted = UserTestUtils.findById(userId);
        assertNotNull(deleted.getDeletedAt());
    }
}