package org.margin.server.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public final class StorageUtils {

    public static final long MAX_CONVERSATION_FILE_BYTES = 5L * 1024 * 1024;

    private StorageUtils() {}

    public static void requireConversationFileSize(MultipartFile file) {
        if (file.getSize() > MAX_CONVERSATION_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "File exceeds the 5 MB limit for conversation images");
        }
    }
}