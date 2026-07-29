package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import static org.margin.server.integrationtest.utils.UserTestUtils.principalOf;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.storage.controllers.FilesController;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class FilesControllerTest extends MarginTestRunner {

    @Autowired
    private FilesController filesController;

    private User owner;
    private User member;
    private User outsider;
    private Long marginId;
    private Long channelId;

    @BeforeEach
    void setUp() {
        owner = UserTestUtils.createUser("fcowner", "fcowner@margin.chat");
        member = UserTestUtils.createUser("fcmember", "fcmember@margin.chat");
        outsider = UserTestUtils.createUser("fcoutsider", "fcoutsider@margin.chat");

        Margin margin = MarginTestUtils.createMargin("FilesCtrlMargin", owner);
        marginId = margin.getId();
        SubscriptionTestUtils.overrideLimits(margin, 25, 100, 10);

        MarginTestUtils.addUserToMargin(marginId, owner, member);

        SpaceDTO space = SpaceTestUtils.createSpace("Test Space", marginId, owner);
        ChannelDTO channel = ChannelTestUtils.createChannel(space.spaceId(), "general", owner);
        channelId = channel.id();
    }

    private String fileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResponseEntity<?> serveStoredFile(String fileName, User viewer) {
        try {
            return filesController.getStoredFileByName(fileName, principalOf(viewer));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    // --- channel-scoped files ---

    @Test
    void channelMemberCanServeChannelFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("chat.txt", "hello"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void outsiderCannotServeChannelFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("chat.txt", "hello"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void unauthenticatedCannotServeChannelFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("chat.txt", "hello"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- margin-scoped files ---

    @Test
    void marginMemberCanServeMarginFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("doc.txt", "content"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void outsiderCannotServeMarginFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("doc.txt", "content"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void unauthenticatedCannotServeMarginFile() {
        StoredFileDTO file = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("doc.txt", "content"), owner).getBody();
        assertNotNull(file);

        ResponseEntity<?> response = serveStoredFile(fileNameFromUrl(file.url()), null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}