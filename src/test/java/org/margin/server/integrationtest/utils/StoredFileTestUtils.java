package org.margin.server.integrationtest.utils;

import org.margin.server.storage.controllers.StoredFileController;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.repositories.StoredFileRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class StoredFileTestUtils {

    private static StoredFileController storedFileController;
    private static StoredFileRepository storedFileRepository;

    @Autowired
    public StoredFileTestUtils(StoredFileController storedFileController,
                               StoredFileRepository storedFileRepository) {
        StoredFileTestUtils.storedFileController = storedFileController;
        StoredFileTestUtils.storedFileRepository = storedFileRepository;
    }

    public static MultipartFile bytes(String filename, String contentType, byte[] data) {
        return new MockMultipartFile("file", filename, contentType, data);
    }

    public static MultipartFile textFile(String filename, String text) {
        return bytes(filename, "text/plain", text.getBytes());
    }

    public static ResponseEntity<List<StoredFileDTO>> listMarginFiles(Long marginId, User viewer) {
        try {
            return storedFileController.listMarginFiles(marginId, viewer);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<List<StoredFileDTO>> listChannelFiles(Long channelId, User viewer) {
        try {
            return storedFileController.listChannelFiles(channelId, viewer);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<StoredFileDTO> uploadMarginFile(Long marginId, MultipartFile file, User user) {
        try {
            return storedFileController.uploadMarginFile(marginId, file, user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<StoredFileDTO> uploadChannelFile(Long channelId, MultipartFile file, User user) {
        try {
            return storedFileController.uploadChannelFile(channelId, file, false, user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<StoredFileDTO> uploadInlineChannelFile(Long channelId, MultipartFile file, User user) {
        try {
            return storedFileController.uploadChannelFile(channelId, file, true, user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<Void> deleteFile(Long fileId, User user) {
        try {
            return storedFileController.deleteFile(fileId, user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<StoredFileDTO> renameFile(Long fileId, String newName, User user) {
        try {
            return storedFileController.renameFile(fileId,
                    new StoredFileController.RenameRequest(newName), user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static ResponseEntity<?> download(Long fileId, User user) {
        try {
            return storedFileController.download(fileId, user);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        }
    }

    public static boolean isSoftDeleted(Long fileId) {
        return storedFileRepository.findById(fileId).isEmpty();
    }
}