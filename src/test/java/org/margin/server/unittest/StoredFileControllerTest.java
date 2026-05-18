package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.ChannelLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.StorageService;
import org.margin.server.storage.StoredFileService;
import org.margin.server.storage.controllers.StoredFileController;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.models.User;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoredFileControllerTest {

    @Mock
    private StorageService storageService;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private StorageProperties.S3Properties s3Properties;
    @Mock
    private StoredFileService storedFileService;
    @Mock
    private MarginLookup marginLookup;
    @Mock
    private ChannelLookup channelLookup;
    @Mock
    private MarginAuthorizationService marginAuthorizationService;
    @Mock
    private ConversationAuthorizationService conversationAuthorizationService;
    @Mock
    private SubscriptionValidationService subscriptionValidationService;

    @InjectMocks
    private StoredFileController controller;

    private User user;
    private Margin margin;
    private Channel channel;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setHandle("alice");

        margin = new Margin();
        margin.setId(7L);

        Conversation conv = new Conversation();
        conv.setId(13L);

        channel = new Channel();
        channel.setId(3L);
        channel.setConversation(conv);
    }

    @Test
    void listMarginFilesIsForbiddenForNonMembers() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginMember(42L, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.listMarginFiles(7L, user));
        verify(storedFileService, never()).findMarginFiles(anyLong());
    }

    @Test
    void listMarginFilesReturnsDtosForMembers() {
        when(storedFileService.findMarginFiles(7L)).thenReturn(List.of(fileEntity(1L, "a.txt")));

        ResponseEntity<List<StoredFileDTO>> response = controller.listMarginFiles(7L, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(1, response.getBody().size());
        assertEquals("a.txt", response.getBody().getFirst().fileName());
    }

    @Test
    void listChannelFilesIsForbiddenForNonMembers() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(conversationAuthorizationService).requireConversationMemberForChannel(3L, 42L);

        assertThrows(ResponseStatusException.class, () -> controller.listChannelFiles(3L, user));
        verify(storedFileService, never()).findChannelFiles(anyLong());
    }

    @Test
    void uploadMarginFilePersistsEntityWithMarginScope() {
        when(marginLookup.getById(7L)).thenReturn(margin);
        when(storageService.saveStoredFile(any())).thenReturn("/api/files/stored-files/x_a.txt");
        when(storedFileService.save(any())).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(101L);
            return f;
        });

        ResponseEntity<StoredFileDTO> response = controller.uploadMarginFile(
                7L, fakeFile("a.txt", "text/plain", "hi"), user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileService).save(captor.capture());
        StoredFile saved = captor.getValue();
        assertEquals(StoredFileScope.MARGIN, saved.getScope());
        assertEquals(margin, saved.getMargin());
        assertNull(saved.getChannel());
        assertEquals("a.txt", saved.getFileName());
        assertEquals(user, saved.getUploadedBy());
    }

    @Test
    void uploadMarginFileForbiddenForRegularMember() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginAdmin(42L, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.uploadMarginFile(
                7L, fakeFile("a.txt", "text/plain", "x"), user));
        verify(storageService, never()).saveStoredFile(any());
        verify(storedFileService, never()).save(any());
    }

    @Test
    void uploadChannelFileAllowedForAnyChannelMember() {
        when(channelLookup.getById(3L)).thenReturn(channelOnMargin());
        when(storageService.saveStoredFile(any())).thenReturn("/api/files/stored-files/y_b.txt");
        when(storedFileService.save(any())).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(102L);
            return f;
        });

        ResponseEntity<StoredFileDTO> response = controller.uploadChannelFile(
                3L, fakeFile("b.txt", "text/plain", "x"), false, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileService).save(captor.capture());
        assertEquals(StoredFileScope.CHANNEL, captor.getValue().getScope());
        assertEquals(channel, captor.getValue().getChannel());
    }

    @Test
    void uploadMarginFileRejectedWhenStorageQuotaExceeded() {
        when(marginLookup.getById(7L)).thenReturn(margin);
        doThrow(new SubscriptionLimitExceededException("Storage quota exceeded", SubscriptionTier.FREE, LimitType.STORAGE))
                .when(subscriptionValidationService).validateStorageQuota(eq(margin), anyLong());

        assertThrows(SubscriptionLimitExceededException.class, () ->
                controller.uploadMarginFile(7L, fakeFile("big.bin", "application/octet-stream", "data"), user));
        verify(storageService, never()).saveStoredFile(any());
        verify(storedFileService, never()).save(any());
    }

    @Test
    void uploadChannelFileRejectedWhenStorageQuotaExceeded() {
        when(channelLookup.getById(3L)).thenReturn(channelOnMargin());
        doThrow(new SubscriptionLimitExceededException("Storage quota exceeded", SubscriptionTier.FREE, LimitType.STORAGE))
                .when(subscriptionValidationService).validateStorageQuota(eq(margin), anyLong());

        assertThrows(SubscriptionLimitExceededException.class, () ->
                controller.uploadChannelFile(3L, fakeFile("img.png", "image/png", "bytes"), false, user));
        verify(storageService, never()).saveStoredFile(any());
        verify(storedFileService, never()).save(any());
    }

    @Test
    void deleteByUploaderSoftDeletesRowAndDeletesBytes() {
        StoredFile existing = fileEntity(55L, "old.txt");
        existing.setUploadedBy(user);
        existing.setStorageUrl("/api/files/stored-files/abc_old.txt");
        when(storedFileService.getById(55L)).thenReturn(existing);

        ResponseEntity<Void> response = controller.deleteFile(55L, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(storedFileService).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
        verify(storageService).delete("/api/files/stored-files/abc_old.txt");
    }

    @Test
    void deleteByNonUploaderNonManagerIsForbidden() {
        User someoneElse = new User();
        someoneElse.setId(999L);
        StoredFile existing = fileEntity(55L, "old.txt");
        existing.setUploadedBy(someoneElse);
        when(storedFileService.getById(55L)).thenReturn(existing);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginAdmin(42L, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.deleteFile(55L, user));
        verify(storedFileService, never()).save(any());
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void deleteByMarginAdminAllowedEvenWhenNotUploader() {
        User otherUploader = new User();
        otherUploader.setId(999L);
        StoredFile existing = fileEntity(55L, "old.txt");
        existing.setUploadedBy(otherUploader);
        existing.setStorageUrl("/api/files/stored-files/x");
        when(storedFileService.getById(55L)).thenReturn(existing);

        ResponseEntity<Void> response = controller.deleteFile(55L, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(storedFileService).save(any());
    }

    @Test
    void downloadRedirectsToPresignedUrlOnS3() {
        StoredFile existing = fileEntity(55L, "report.pdf");
        existing.setStorageUrl("/api/files/stored-files/x_report.pdf");
        when(storedFileService.getById(55L)).thenReturn(existing);
        when(storageProperties.getType()).thenReturn("s3");
        when(storageService.presign(existing.getStorageUrl(), "report.pdf"))
                .thenReturn(Optional.of("https://hetzner.example/signed-link"));

        ResponseEntity<Resource> response = controller.download(55L, user);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://hetzner.example/signed-link",
                response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void downloadStreamsBytesOnLocalStorage() throws Exception {
        StoredFile existing = fileEntity(55L, "report.pdf");
        existing.setStorageUrl("/api/files/stored-files/x_report.pdf");
        existing.setContentType("application/pdf");
        when(storedFileService.getById(55L)).thenReturn(existing);
        when(storageProperties.getType()).thenReturn("local");
        when(storageService.getFile(existing.getStorageUrl()))
                .thenReturn(new ByteArrayResource("hello".getBytes()));

        ResponseEntity<Resource> response = controller.download(55L, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.contains("report.pdf"));
        verify(storageService, never()).presign(anyString(), any());
    }

    @Test
    void downloadFallsBackToStreamWhenPresignReturnsEmpty() throws Exception {
        StoredFile existing = fileEntity(55L, "a.bin");
        existing.setStorageUrl("/api/files/stored-files/x");
        existing.setContentType("application/octet-stream");
        when(storedFileService.getById(55L)).thenReturn(existing);
        when(storageProperties.getType()).thenReturn("s3");
        when(storageService.presign(eq(existing.getStorageUrl()), eq("a.bin"))).thenReturn(Optional.empty());
        when(storageService.getFile(existing.getStorageUrl()))
                .thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        ResponseEntity<Resource> response = controller.download(55L, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void downloadForbiddenForNonMember() {
        StoredFile existing = fileEntity(55L, "report.pdf");
        when(storedFileService.getById(55L)).thenReturn(existing);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginMember(42L, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.download(55L, user));
    }

    private StoredFile fileEntity(Long id, String name) {
        StoredFile f = new StoredFile();
        f.setId(id);
        f.setScope(StoredFileScope.MARGIN);
        f.setMargin(margin);
        f.setFileName(name);
        f.setContentType("text/plain");
        f.setSizeBytes(5);
        f.setStorageUrl("/api/files/stored-files/" + id + "_" + name);
        f.setUploadedBy(user);
        return f;
    }

    private Channel channelOnMargin() {
        channel.setSpace(stubSpaceOnMargin());
        return channel;
    }

    private org.margin.server.social.space.models.Space stubSpaceOnMargin() {
        org.margin.server.social.space.models.Space space = new org.margin.server.social.space.models.Space();
        space.setMargin(margin);
        return space;
    }

    private MultipartFile fakeFile(String name, String type, String content) {
        return new MockMultipartFile("file", name, type, content.getBytes());
    }
}