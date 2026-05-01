package org.margin.server.storage.controllers;

import io.github.bucket4j.Bandwidth;
import org.margin.server.config.ratelimit.RateLimitService;
import org.margin.server.exceptions.TooManyRequestsException;
import org.margin.server.storage.StorageService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FilesController {
    private final StorageService storageService;
    private final RateLimitService rateLimitService;
    private final Bandwidth conversationImageUploadBandwidth;

    public FilesController(StorageService storageService,
                           RateLimitService rateLimitService,
                           @Value("${margin.rate-limit.conversation-image-upload.capacity}") int capacity,
                           @Value("${margin.rate-limit.conversation-image-upload.refill-period}") Duration refillPeriod) {
        this.storageService = storageService;
        this.rateLimitService = rateLimitService;
        this.conversationImageUploadBandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
    }

    @PostMapping("/upload_to_conversation")
    public String upload(@RequestParam("attachedFile") MultipartFile file,
                         @AuthenticationPrincipal User user) {
        String key = "upload_to_conversation:" + user.getId();
        if (!rateLimitService.tryConsume(key, conversationImageUploadBandwidth)) {
            throw new TooManyRequestsException("Image upload limit reached. Try again later.");
        }
        return storageService.saveConversationImage(file);
    }

    @GetMapping("/conversation-images/{fileName}")
    public ResponseEntity<Resource> getConversationImage(@PathVariable String fileName) {
        try {
            Resource resource = storageService.getFile("/conversation-images/" + fileName);

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
