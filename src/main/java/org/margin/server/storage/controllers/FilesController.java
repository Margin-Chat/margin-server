package org.margin.server.storage.controllers;

import org.margin.server.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FilesController {
    private final StorageService storageService;

    public FilesController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/user-profiles/{fileName}")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable String fileName) {
        try {
            Resource resource = storageService.getFile("/user-profiles/" + fileName);

            String contentType = Files.probeContentType(Path.of(fileName));
            return ResponseEntity.ok()
                    .contentType(contentType != null
                            ? MediaType.parseMediaType(contentType)
                            : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (NoSuchFileException _) {
            return ResponseEntity.notFound().build();
        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/margin-icons/{fileName}")
    public ResponseEntity<Resource> getMarginIcon(@PathVariable String fileName) {
        try {
            Resource resource = storageService.getFile("/margin-icons/" + fileName);

            String contentType = Files.probeContentType(Path.of(fileName));
            return ResponseEntity.ok()
                    .contentType(contentType != null
                            ? MediaType.parseMediaType(contentType)
                            : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (NoSuchFileException _) {
            return ResponseEntity.notFound().build();
        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
