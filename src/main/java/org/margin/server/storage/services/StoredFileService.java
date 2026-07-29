package org.margin.server.storage.services;

import org.margin.server.presence.PresenceService;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.exceptions.StoredFileNotFoundException;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.social.api.ChannelLookup;
import org.margin.server.social.api.MessageAttachmentDTO;
import org.margin.server.social.api.MessageAttachmentLookup;
import org.margin.server.storage.repositories.StoredFileRepository;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StoredFileService implements MessageAttachmentLookup {

    private static final int MAX_ATTACHMENTS_PER_MESSAGE = 10;

    private final StoredFileRepository storedFileRepository;
    private final PresenceService presenceService;
    private final StorageService storageService;
    private final SubscriptionValidationService subscriptionValidationService;
    private final ChannelLookup channelLookup;
    private final UserLookup userLookup;

    public StoredFileService(StoredFileRepository storedFileRepository,
                             PresenceService presenceService,
                             StorageService storageService,
                             SubscriptionValidationService subscriptionValidationService,
                             ChannelLookup channelLookup,
                             UserLookup userLookup) {
        this.storedFileRepository = storedFileRepository;
        this.presenceService = presenceService;
        this.storageService = storageService;
        this.subscriptionValidationService = subscriptionValidationService;
        this.channelLookup = channelLookup;
        this.userLookup = userLookup;
    }

    @Transactional
    public StoredFile uploadMarginFile(Long marginId, MultipartFile file, Long uploaderId) {
        validateQuota(marginId, file.getSize());
        String url = storageService.saveStoredFile(file);
        StoredFile entity = new StoredFile();
        entity.setScope(StoredFileScope.MARGIN);
        entity.setMarginId(marginId);
        entity.setFileName(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setStorageUrl(url);
        entity.setUploadedByUserId(uploaderId);
        return storedFileRepository.save(entity);
    }

    @Transactional
    public StoredFile uploadChannelFile(Long channelId, MultipartFile file, Long uploaderId, boolean inline) {
        Long marginId = channelLookup.marginIdOf(channelId);
        validateQuota(marginId, file.getSize());
        String url = storageService.saveStoredFile(file);
        StoredFile entity = new StoredFile();
        entity.setScope(StoredFileScope.CHANNEL);
        entity.setMarginId(marginId);
        entity.setChannelId(channelId);
        entity.setFileName(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setStorageUrl(url);
        entity.setUploadedByUserId(uploaderId);
        entity.setInline(inline);
        return storedFileRepository.save(entity);
    }

    @Transactional
    public StoredFile uploadConversationFile(Long conversationId, MultipartFile file, Long uploaderId, boolean inline) {
        String url = storageService.saveStoredFile(file);
        StoredFile entity = new StoredFile();
        entity.setScope(StoredFileScope.CONVERSATION);
        entity.setConversationId(conversationId);
        entity.setFileName(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setStorageUrl(url);
        entity.setUploadedByUserId(uploaderId);
        entity.setInline(inline);
        return storedFileRepository.save(entity);
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
    public Map<Long, List<MessageAttachmentDTO>> findByMessageIds(List<Long> messageIds) {
        return storedFileRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(
                        StoredFile::getMessageId,
                        Collectors.mapping(
                                f -> toAttachment(StoredFileDTO.from(f, userLookup.dtoOf(f.getUploadedByUserId()))),
                                Collectors.toList()
                        )
                ));
    }

    private static MessageAttachmentDTO toAttachment(StoredFileDTO f) {
        return new MessageAttachmentDTO(f.fileId(), f.scope().name(), f.marginId(), f.channelId(),
                f.conversationId(), f.fileName(), f.contentType(), f.sizeBytes(), f.url(),
                f.uploadedBy(), f.uploadedAt());
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
            if (!file.getUploadedByUserId().equals(senderId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment not owned by sender");
            }
            if (file.getMessageId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Attachment already linked to a message");
            }
            if (channelId != null && (file.getChannelId() == null || !file.getChannelId().equals(channelId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment from different channel");
            }
            file.setMessageId(messageId);
        }
        storedFileRepository.saveAll(files);
    }

    private void validateQuota(Long marginId, long newFileBytes) {
        subscriptionValidationService.validateStorageQuota(marginId,
                storedFileRepository.sumSizeBytesByMargin(marginId), newFileBytes);
    }

    @Transactional(readOnly = true)
    public List<StoredFileDTO> toDTOs(List<StoredFile> files) {
        Map<Long, UserDTO> uploaders = userLookup.dtosOf(
                        files.stream().map(StoredFile::getUploadedByUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(UserDTO::id, u -> u));

        return files.stream()
                .map(f -> StoredFileDTO.from(f, uploaders.get(f.getUploadedByUserId())))
                .toList();
    }
}