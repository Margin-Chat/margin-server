package org.margin.server.storage;

import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.exceptions.StoredFileNotFoundException;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.repositories.StoredFileRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StoredFileService implements StorageLookup {

    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;

    private final StoredFileRepository storedFileRepository;
    private final ConnectionManager connectionManager;

    public StoredFileService(StoredFileRepository storedFileRepository, ConnectionManager connectionManager) {
        this.storedFileRepository = storedFileRepository;
        this.connectionManager = connectionManager;
    }

    @Transactional(readOnly = true)
    public List<StoredFile> findMarginFiles(Long marginId) {
        return storedFileRepository.findMarginFiles(marginId);
    }

    @Transactional(readOnly = true)
    public List<StoredFile> findChannelFiles(Long channelId) {
        return storedFileRepository.findChannelFiles(channelId);
    }

    @Transactional
    public StoredFile save(StoredFile file) {
        return storedFileRepository.save(file);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<StoredFileDTO>> findAttachmentsByMessageIds(List<Long> messageIds) {
        return storedFileRepository.findByMessageIds(messageIds).stream()
                .collect(Collectors.groupingBy(
                        StoredFile::getMessageId,
                        Collectors.mapping(
                                f -> StoredFileDTO.from(f, connectionManager.isUserOnline(f.getUploadedBy().getId())),
                                Collectors.toList()
                        )
                ));
    }

    @Transactional(readOnly = true)
    public StoredFile getById(Long fileId) {
        return storedFileRepository.findById(fileId)
                .orElseThrow(() -> new StoredFileNotFoundException(fileId));
    }

    @Transactional(readOnly = true)
    public StoredFile findByStoredFileName(String fileName) {
        return storedFileRepository.findFirstByStorageUrlEndsWith("/stored-files/" + fileName)
                .orElseThrow(() -> new StoredFileNotFoundException(fileName));
    }

    @Transactional(readOnly = true)
    public StoredFile findByConversationImageFileName(String fileName) {
        return storedFileRepository.findFirstByStorageUrlEndsWith("/conversation-images/" + fileName)
                .orElseThrow(() -> new StoredFileNotFoundException(fileName));
    }

    @Transactional
    public void linkAttachmentsToMessage(Long messageId, Long senderId, Long channelId, List<Long> attachmentIds) {
        if (attachmentIds.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_ATTACHMENTS_PER_MESSAGE + " attachments per message");
        }
        List<StoredFile> files = storedFileRepository.findAllById(attachmentIds);
        if (files.size() != attachmentIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more attachments not found");
        }
        for (StoredFile file : files) {
            if (!file.getUploadedBy().getId().equals(senderId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment not owned by sender");
            }
            if (file.getMessageId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Attachment already linked to a message");
            }
            if (channelId != null && (file.getChannel() == null || !file.getChannel().getId().equals(channelId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment from different channel");
            }
            file.setMessageId(messageId);
        }
        storedFileRepository.saveAll(files);
    }
}