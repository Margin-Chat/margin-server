package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoredFileTest extends MarginTestRunner {

    private User owner;
    private User admin;
    private User member;
    private User outsider;
    private Long marginId;
    private Long channelId;

    @BeforeEach
    void setUp() {
        owner = UserTestUtils.createUser("filesowner", "files-owner@margin.chat");
        admin = UserTestUtils.createUser("filesadmin", "files-admin@margin.chat");
        member = UserTestUtils.createUser("filesmember", "files-member@margin.chat");
        outsider = UserTestUtils.createUser("filesoutsider", "files-outsider@margin.chat");

        Margin margin = MarginTestUtils.createMargin("FilesMargin", owner);
        marginId = margin.getId();
        SubscriptionTestUtils.overrideLimits(margin, 25, 100, 10);

        MarginTestUtils.addUserToMargin(marginId, owner, admin);
        MarginTestUtils.updateMemberRole(marginId, admin, MarginRole.ADMIN, owner);
        MarginTestUtils.addUserToMargin(marginId, owner, member);

        SpaceDTO space = SpaceTestUtils.createSpace("Files Space", marginId, owner);
        ChannelDTO channel = ChannelTestUtils.createChannel(space.spaceId(), "general", owner);
        channelId = channel.id();
    }

    @Test
    void ownerCanUploadMarginFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("notes.txt", "hello"), owner);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        StoredFileDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(StoredFileScope.MARGIN, body.scope());
        assertEquals(marginId, body.marginId());
        assertNull(body.channelId());
        assertEquals("notes.txt", body.fileName());
        assertEquals(owner.getId(), body.uploadedBy().id());
    }

    @Test
    void adminCanUploadMarginFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("admin.txt", "x"), admin);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void regularMemberCannotUploadMarginFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("member.txt", "x"), member);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void outsiderCannotUploadMarginFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("o.txt", "x"), outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void anyChannelMemberCanUploadChannelFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("chat.txt", "hi"), member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        StoredFileDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(StoredFileScope.CHANNEL, body.scope());
        assertEquals(channelId, body.channelId());
        assertEquals(marginId, body.marginId());
    }

    @Test
    void outsiderCannotUploadChannelFile() {
        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("o.txt", "x"), outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void memberSeesMarginFiles() {
        StoredFileTestUtils.uploadMarginFile(marginId, StoredFileTestUtils.textFile("a.txt", "1"), owner);
        StoredFileTestUtils.uploadMarginFile(marginId, StoredFileTestUtils.textFile("b.txt", "2"), admin);

        ResponseEntity<List<StoredFileDTO>> response = StoredFileTestUtils.listMarginFiles(marginId, member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void outsiderCannotListMarginFiles() {
        ResponseEntity<List<StoredFileDTO>> response = StoredFileTestUtils.listMarginFiles(marginId, outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void channelMemberSeesChannelFiles() {
        StoredFileTestUtils.uploadChannelFile(channelId, StoredFileTestUtils.textFile("a.txt", "1"), member);

        ResponseEntity<List<StoredFileDTO>> response = StoredFileTestUtils.listChannelFiles(channelId, owner);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void outsiderCannotListChannelFiles() {
        ResponseEntity<List<StoredFileDTO>> response = StoredFileTestUtils.listChannelFiles(channelId, outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void channelListingExcludesMarginFiles() {
        StoredFileTestUtils.uploadMarginFile(marginId, StoredFileTestUtils.textFile("margin.txt", "x"), owner);
        StoredFileTestUtils.uploadChannelFile(channelId, StoredFileTestUtils.textFile("channel.txt", "x"), member);

        List<StoredFileDTO> channelFiles = StoredFileTestUtils.listChannelFiles(channelId, member).getBody();
        List<StoredFileDTO> marginFiles = StoredFileTestUtils.listMarginFiles(marginId, member).getBody();

        assertEquals(1, channelFiles.size());
        assertEquals("channel.txt", channelFiles.getFirst().fileName());
        assertEquals(1, marginFiles.size());
        assertEquals("margin.txt", marginFiles.getFirst().fileName());
    }

    @Test
    void uploaderCanDeleteOwnChannelFile() {
        Long fileId = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("mine.txt", "x"), member).getBody().fileId();

        ResponseEntity<Void> response = StoredFileTestUtils.deleteFile(fileId, member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StoredFileTestUtils.isSoftDeleted(fileId));
    }

    @Test
    void marginAdminCanDeleteAnyFile() {
        Long fileId = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("notmine.txt", "x"), member).getBody().fileId();

        ResponseEntity<Void> response = StoredFileTestUtils.deleteFile(fileId, admin);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(StoredFileTestUtils.isSoftDeleted(fileId));
    }

    @Test
    void regularMemberCannotDeleteOthersFile() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("owner.txt", "x"), owner).getBody().fileId();

        ResponseEntity<Void> response = StoredFileTestUtils.deleteFile(fileId, member);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse(StoredFileTestUtils.isSoftDeleted(fileId));
    }

    @Test
    void deletedFileDoesNotAppearInListings() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("doomed.txt", "x"), owner).getBody().fileId();
        assertEquals(1, StoredFileTestUtils.listMarginFiles(marginId, member).getBody().size());

        StoredFileTestUtils.deleteFile(fileId, owner);

        assertEquals(0, StoredFileTestUtils.listMarginFiles(marginId, member).getBody().size());
    }

    @Test
    void memberCanDownloadMarginFile() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("dl.txt", "payload"), owner).getBody().fileId();

        ResponseEntity<?> response = StoredFileTestUtils.download(fileId, member);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void outsiderCannotDownload() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("p.txt", "x"), owner).getBody().fileId();

        ResponseEntity<?> response = StoredFileTestUtils.download(fileId, outsider);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void downloadingMissingFileReturnsNotFound() {
        ResponseEntity<?> response = StoredFileTestUtils.download(999_999L, member);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void uploaderCanRename() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("old.txt", "x"), owner).getBody().fileId();

        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.renameFile(fileId, "new.txt", owner);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("new.txt", response.getBody().fileName());
    }

    @Test
    void adminCanRenameOthersFile() {
        Long fileId = StoredFileTestUtils.uploadChannelFile(
                channelId, StoredFileTestUtils.textFile("by-member.txt", "x"), member).getBody().fileId();

        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.renameFile(fileId, "renamed.txt", admin);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("renamed.txt", response.getBody().fileName());
    }

    @Test
    void regularMemberCannotRenameOthersFile() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("o.txt", "x"), owner).getBody().fileId();

        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.renameFile(fileId, "x.txt", member);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void renamingWithBlankNameRejected() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("a.txt", "x"), owner).getBody().fileId();

        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.renameFile(fileId, "   ", owner);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void renamingWithTooLongNameRejected() {
        Long fileId = StoredFileTestUtils.uploadMarginFile(
                marginId, StoredFileTestUtils.textFile("a.txt", "x"), owner).getBody().fileId();

        ResponseEntity<StoredFileDTO> response = StoredFileTestUtils.renameFile(fileId, "x".repeat(513), owner);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
