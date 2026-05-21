package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.StorageService;
import org.margin.server.storage.StoredFileService;
import org.margin.server.storage.controllers.FilesController;
import org.margin.server.storage.exceptions.StoredFileNotFoundException;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilesControllerTest {

    @Mock
    private StorageService storageService;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private StorageProperties.S3Properties s3Properties;
    @Mock
    private MarginLookup marginLookup;
    @Mock
    private StoredFileService storedFileService;
    @Mock
    private MarginAuthorizationService marginAuthorizationService;
    @Mock
    private ConversationAuthorizationService conversationAuthorizationService;

    @InjectMocks
    private FilesController controller;

    private User viewer;
    private Margin margin;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setId(1L);

        margin = new Margin();
        margin.setId(10L);
        margin.setIconUrl("/api/files/margin-icons/icon.png");
    }

    // --- getProfilePicture ---

    @Test
    void getProfilePicture_returnsUnauthorizedWhenNotAuthenticated() {
        ResponseEntity<?> response = controller.getProfilePicture("avatar.png", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getProfilePicture_servesFileOnLocalStorage() throws IOException {
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile("/user-profiles/avatar.png"))
                .thenReturn(new ByteArrayResource("bytes".getBytes()));

        ResponseEntity<?> response = controller.getProfilePicture("avatar.png", viewer);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(storageService).getFile("/user-profiles/avatar.png");
    }

    @Test
    void getProfilePicture_redirectsToPresignedUrlOnS3() throws IOException {
        when(storageProperties.getType()).thenReturn("s3");
        when(storageService.presign("/user-profiles/avatar.png"))
                .thenReturn(Optional.of("https://s3.example/signed"));
        when(storageProperties.getS3()).thenReturn(s3Properties);
        when(s3Properties.getPresignTtlSeconds()).thenReturn(600L);

        ResponseEntity<?> response = controller.getProfilePicture("avatar.png", viewer);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://s3.example/signed",
                response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void getProfilePicture_returnsNotFoundForMissingFile() throws IOException {
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile(anyString())).thenThrow(new NoSuchFileException("avatar.png"));

        ResponseEntity<?> response = controller.getProfilePicture("avatar.png", viewer);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // --- getMarginIcon ---

    @Test
    void getMarginIcon_returnsUnauthorizedWhenNotAuthenticated() {
        ResponseEntity<?> response = controller.getMarginIcon("icon.png", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getMarginIcon_requiresMarginMembership() throws IOException {
        when(marginLookup.findByIconFileName("icon.png")).thenReturn(margin);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginMember(1L, 10L);

        assertThrows(ResponseStatusException.class,
                () -> controller.getMarginIcon("icon.png", viewer));
        verify(storageService, never()).getFile(anyString());
    }

    @Test
    void getMarginIcon_servesFileForMarginMember() throws IOException {
        when(marginLookup.findByIconFileName("icon.png")).thenReturn(margin);
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile("/api/files/margin-icons/icon.png"))
                .thenReturn(new ByteArrayResource("icon".getBytes()));

        ResponseEntity<?> response = controller.getMarginIcon("icon.png", viewer);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    // --- getStoredFileByName ---

    @Test
    void getStoredFileByName_returnsUnauthorizedWhenNotAuthenticated() {
        ResponseEntity<?> response = controller.getStoredFileByName("file.pdf", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getStoredFileByName_requiresConversationMembershipForChannelFile() throws IOException {
        StoredFile f = channelStoredFile();
        when(storedFileService.findByStoredFileName("file.pdf")).thenReturn(f);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(conversationAuthorizationService).requireConversationMember(1L, 99L);

        assertThrows(ResponseStatusException.class,
                () -> controller.getStoredFileByName("file.pdf", viewer));
        verify(storageService, never()).getFile(anyString());
    }

    @Test
    void getStoredFileByName_requiresMarginMembershipForMarginFile() throws IOException {
        StoredFile f = marginStoredFile();
        when(storedFileService.findByStoredFileName("file.pdf")).thenReturn(f);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginMember(1L, 10L);

        assertThrows(ResponseStatusException.class,
                () -> controller.getStoredFileByName("file.pdf", viewer));
        verify(storageService, never()).getFile(anyString());
    }

    @Test
    void getStoredFileByName_servesFileAfterAuthCheck() throws IOException {
        StoredFile f = marginStoredFile();
        when(storedFileService.findByStoredFileName("file.pdf")).thenReturn(f);
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile(f.getStorageUrl()))
                .thenReturn(new ByteArrayResource("data".getBytes()));

        ResponseEntity<?> response = controller.getStoredFileByName("file.pdf", viewer);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(marginAuthorizationService).requireMarginMember(1L, 10L);
    }

    @Test
    void getStoredFileByName_returnsNotFoundWhenFileDoesNotExist() {
        when(storedFileService.findByStoredFileName("missing.pdf"))
                .thenThrow(new StoredFileNotFoundException("missing.pdf"));

        assertThrows(StoredFileNotFoundException.class,
                () -> controller.getStoredFileByName("missing.pdf", viewer));
    }

    // --- getConversationImage ---

    @Test
    void getConversationImage_returnsUnauthorizedWhenNotAuthenticated() {
        ResponseEntity<?> response = controller.getConversationImage("img.png", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getConversationImage_returnsNotFoundWhenFileHasNoMessageId() {
        StoredFile f = channelStoredFile();
        f.setMessageId(null);
        when(storedFileService.findByConversationImageFileName("img.png")).thenReturn(f);

        ResponseEntity<?> response = controller.getConversationImage("img.png", viewer);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getConversationImage_returnsNotFoundWhenFileHasNoChannel() {
        StoredFile f = marginStoredFile();
        f.setMessageId(42L);
        when(storedFileService.findByConversationImageFileName("img.png")).thenReturn(f);

        ResponseEntity<?> response = controller.getConversationImage("img.png", viewer);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getConversationImage_requiresConversationMembership() throws IOException {
        StoredFile f = channelStoredFile();
        when(storedFileService.findByConversationImageFileName("img.png")).thenReturn(f);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(conversationAuthorizationService).requireConversationMember(1L, 99L);

        assertThrows(ResponseStatusException.class,
                () -> controller.getConversationImage("img.png", viewer));
        verify(storageService, never()).getFile(anyString());
    }

    @Test
    void getConversationImage_servesFileAfterAuthCheck() throws IOException {
        StoredFile f = channelStoredFile();
        when(storedFileService.findByConversationImageFileName("img.png")).thenReturn(f);
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile(f.getStorageUrl()))
                .thenReturn(new ByteArrayResource("img".getBytes()));

        ResponseEntity<?> response = controller.getConversationImage("img.png", viewer);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(conversationAuthorizationService).requireConversationMember(1L, 99L);
    }

    // --- helpers ---

    private StoredFile channelStoredFile() {
        Conversation conv = new Conversation();
        conv.setId(99L);

        org.margin.server.social.channel.entities.Channel ch =
                new org.margin.server.social.channel.entities.Channel();
        ch.setId(5L);
        ch.setConversation(conv);

        StoredFile f = new StoredFile();
        f.setId(1L);
        f.setScope(StoredFileScope.CHANNEL);
        f.setMargin(margin);
        f.setChannel(ch);
        f.setMessageId(42L);
        f.setFileName("file.pdf");
        f.setContentType("application/pdf");
        f.setSizeBytes(100L);
        f.setStorageUrl("/api/files/stored-files/uuid_file.pdf");
        f.setUploadedBy(viewer);
        return f;
    }

    private StoredFile marginStoredFile() {
        StoredFile f = new StoredFile();
        f.setId(2L);
        f.setScope(StoredFileScope.MARGIN);
        f.setMargin(margin);
        f.setChannel(null);
        f.setFileName("file.pdf");
        f.setContentType("application/pdf");
        f.setSizeBytes(100L);
        f.setStorageUrl("/api/files/stored-files/uuid_file.pdf");
        f.setUploadedBy(viewer);
        return f;
    }
}