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
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.models.SubscriptionTier;
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
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.margin.server.unittest.utils.ChannelTestUtils.createChannel;
import static org.margin.server.unittest.utils.StoredFileTestUtils.marginFile;

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
    @InjectMocks
    private StoredFileController controller;

    private User user;
    private Margin margin;
    private Channel channel;

    @BeforeEach
    void setUp() {
        user = createUser(42L, "alice");

        margin = new Margin();
        margin.setId(7L);

        Conversation conv = new Conversation();
        conv.setId(13L);

        channel = createChannel(3L);
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
    void uploadMarginFileDelegatesToService() {
        when(marginLookup.getById(7L)).thenReturn(margin);
        when(storedFileService.uploadMarginFile(eq(margin), any(), eq(user)))
                .thenReturn(fileEntity(101L, "a.txt"));

        ResponseEntity<StoredFileDTO> response = controller.uploadMarginFile(
                7L, fakeFile("a.txt", "text/plain", "hi"), user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(storedFileService).uploadMarginFile(eq(margin), any(), eq(user));
    }

    @Test
    void uploadMarginFileForbiddenForRegularMember() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(marginAuthorizationService).requireMarginAdmin(42L, 7L);

        assertThrows(ResponseStatusException.class, () -> controller.uploadMarginFile(
                7L, fakeFile("a.txt", "text/plain", "x"), user));
        verify(storedFileService, never()).uploadMarginFile(any(), any(), any());
    }

    @Test
    void uploadChannelFileDelegatesToService() {
        when(channelLookup.getById(3L)).thenReturn(channelOnMargin());
        when(storedFileService.uploadChannelFile(eq(channel), any(), eq(user), eq(false)))
                .thenReturn(fileEntity(102L, "b.txt"));

        ResponseEntity<StoredFileDTO> response = controller.uploadChannelFile(
                3L, fakeFile("b.txt", "text/plain", "x"), false, user);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(storedFileService).uploadChannelFile(eq(channel), any(), eq(user), eq(false));
    }

    @Test
    void uploadMarginFileForbiddenWhenServiceThrowsQuotaException() {
        when(marginLookup.getById(7L)).thenReturn(margin);
        doThrow(new SubscriptionLimitExceededException("Storage quota exceeded", SubscriptionTier.FREE, LimitType.STORAGE))
                .when(storedFileService).uploadMarginFile(eq(margin), any(), eq(user));

        assertThrows(SubscriptionLimitExceededException.class, () ->
                controller.uploadMarginFile(7L, fakeFile("big.bin", "application/octet-stream", "data"), user));
    }

    @Test
    void uploadChannelFileForbiddenWhenServiceThrowsQuotaException() {
        when(channelLookup.getById(3L)).thenReturn(channelOnMargin());
        doThrow(new SubscriptionLimitExceededException("Storage quota exceeded", SubscriptionTier.FREE, LimitType.STORAGE))
                .when(storedFileService).uploadChannelFile(eq(channel), any(), eq(user), eq(false));

        assertThrows(SubscriptionLimitExceededException.class, () ->
                controller.uploadChannelFile(3L, fakeFile("img.png", "image/png", "bytes"), false, user));
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
        User someoneElse = createUser(999L);
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
        User otherUploader = createUser(999L);
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
        return marginFile(id, name, margin, user);
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