package org.margin.server.storage.dtos;

import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.storage.services.StorageUrls;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record StoredFileDTO(
        Long fileId,
        StoredFileScope scope,
        Long marginId,
        Long channelId,
        Long conversationId,
        String fileName,
        String contentType,
        long sizeBytes,
        String url,
        UserDTO uploadedBy,
        Instant uploadedAt
) {
    public static StoredFileDTO from(StoredFile f, User uploader, boolean uploaderOnline) {
        return new StoredFileDTO(
                f.getId(),
                f.getScope(),
                f.getMarginId(),
                f.getChannelId(),
                f.getConversationId(),
                f.getFileName(),
                f.getContentType(),
                f.getSizeBytes(),
                StorageUrls.signedUrl(f.getStorageUrl()),
                new UserDTO(uploader, uploaderOnline),
                f.getUploadedAt()
        );
    }
}
