package org.margin.server.storage.listeners;

import org.margin.server.social.margin.events.MessageAttachmentsCreatedEvent;
import org.margin.server.storage.services.StoredFileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StorageEventListener {

    private final StoredFileService storedFileService;

    public StorageEventListener(StoredFileService storedFileService) {
        this.storedFileService = storedFileService;
    }

    @EventListener
    public void onMessageAttachmentsCreated(MessageAttachmentsCreatedEvent event) {
        storedFileService.linkAttachmentsToMessage(
                event.getMessageId(),
                event.getSenderId(),
                event.getChannelId(),
                event.getAttachmentIds()
        );
    }
}