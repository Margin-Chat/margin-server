package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.storage.StorageService;
import org.margin.server.storage.StoredFileService;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.storage.repositories.StoredFileRepository;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock private StoredFileRepository storedFileRepository;
    @Mock private ConnectionManager connectionManager;
    @Mock private StorageService storageService;
    @Mock private SubscriptionValidationService subscriptionValidationService;

    @InjectMocks
    private StoredFileService service;

    private User sender;
    private org.margin.server.social.channel.entities.Channel channel;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(10L);

        channel = new org.margin.server.social.channel.entities.Channel();
        channel.setId(3L);
    }

    @Test
    void linkAttachmentsToMessage_setsMessageIdOnAllFiles() {
        StoredFile f1 = file(1L, sender, channel);
        StoredFile f2 = file(2L, sender, channel);
        when(storedFileRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(f1, f2));

        service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), List.of(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoredFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(storedFileRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allMatch(f -> f.getMessageId().equals(99L));
    }

    @Test
    void linkAttachmentsToMessage_rejectsMoreThanTenAttachments() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 11; i++) ids.add(i);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), ids));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(storedFileRepository, never()).findAllById(anyList());
    }

    @Test
    void linkAttachmentsToMessage_rejectsWhenSomeAttachmentsNotFound() {
        when(storedFileRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(file(1L, sender, channel)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), List.of(1L, 2L)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(storedFileRepository, never()).saveAll(anyList());
    }

    @Test
    void linkAttachmentsToMessage_rejectsFileOwnedByDifferentUser() {
        User otherUser = new User();
        otherUser.setId(999L);
        StoredFile f = file(1L, otherUser, channel);
        when(storedFileRepository.findAllById(List.of(1L))).thenReturn(List.of(f));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), List.of(1L)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(storedFileRepository, never()).saveAll(anyList());
    }

    @Test
    void linkAttachmentsToMessage_rejectsAlreadyLinkedFile() {
        StoredFile f = file(1L, sender, channel);
        f.setMessageId(77L);
        when(storedFileRepository.findAllById(List.of(1L))).thenReturn(List.of(f));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), List.of(1L)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(storedFileRepository, never()).saveAll(anyList());
    }

    @Test
    void linkAttachmentsToMessage_rejectsFileFromDifferentChannel() {
        org.margin.server.social.channel.entities.Channel otherChannel =
                new org.margin.server.social.channel.entities.Channel();
        otherChannel.setId(999L);
        StoredFile f = file(1L, sender, otherChannel);
        when(storedFileRepository.findAllById(List.of(1L))).thenReturn(List.of(f));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.linkAttachmentsToMessage(99L, sender.getId(), channel.getId(), List.of(1L)));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(storedFileRepository, never()).saveAll(anyList());
    }

    private StoredFile file(Long id, User owner, org.margin.server.social.channel.entities.Channel ch) {
        StoredFile f = new StoredFile();
        f.setId(id);
        f.setScope(StoredFileScope.CHANNEL);
        f.setUploadedBy(owner);
        f.setChannel(ch);
        f.setFileName("file.txt");
        f.setContentType("text/plain");
        f.setSizeBytes(10L);
        f.setStorageUrl("/api/files/stored-files/uuid_file.txt");
        return f;
    }
}