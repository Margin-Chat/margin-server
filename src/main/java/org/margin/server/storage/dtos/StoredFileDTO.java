package org.margin.server.storage.dtos;

import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record StoredFileDTO(
        Long fileId,
        StoredFileScope scope,
        Long marginId,
        Long channelId,
        String fileName,
        String contentType,
        long sizeBytes,
        String url,
        UserDTO uploadedBy,
        Instant uploadedAt
) {
    public static StoredFileDTO from(StoredFile f, boolean uploaderOnline) {
        return new StoredFileDTO(
                f.getId(),
                f.getScope(),
                f.getMargin().getId(),
                f.getChannel() != null ? f.getChannel().getId() : null,
                f.getFileName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getStorageUrl(),
                new UserDTO(f.getUploadedBy(), uploaderOnline),
                f.getUploadedAt()
        );
    }
}
