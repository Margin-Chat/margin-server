package org.margin.server.storage.controllers;

import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.storage.services.StorageService;
import org.margin.server.storage.services.StoredFileService;
import org.margin.server.users.models.User;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
public class FilesController {
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final MarginLookup marginLookup;
    private final StoredFileService storedFileService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final ConversationAuthorizationService conversationAuthorizationService;

    public FilesController(StorageService storageService,
                           StorageProperties storageProperties,
                           MarginLookup marginLookup,
                           StoredFileService storedFileService,
                           MarginAuthorizationService marginAuthorizationService,
                           ConversationAuthorizationService conversationAuthorizationService) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.marginLookup = marginLookup;
        this.storedFileService = storedFileService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.conversationAuthorizationService = conversationAuthorizationService;
    }

    @GetMapping("/user-profiles/{fileName}")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable String fileName,
                                                      @AuthenticationPrincipal User viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return serve("/user-profiles/" + fileName, fileName);
    }

    @GetMapping("/margin-icons/{fileName}")
    public ResponseEntity<Resource> getMarginIcon(@PathVariable String fileName,
                                                  @AuthenticationPrincipal User viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Margin margin = marginLookup.findByIconFileName(fileName);
        marginAuthorizationService.requireMarginMember(viewer.getId(), margin.getId());
        return serve(margin.getIconUrl(), fileName);
    }

    @GetMapping("/stored-files/{fileName}")
    public ResponseEntity<Resource> getStoredFileByName(@PathVariable String fileName,
                                                        @AuthenticationPrincipal User viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StoredFile f = storedFileService.findByStoredFileName(fileName);

        if (f.getScope() == StoredFileScope.CHANNEL) {
            conversationAuthorizationService.requireConversationMember(f.getChannel().getConversation().getId(), viewer.getId());
        } else if (f.getScope() == StoredFileScope.CONVERSATION) {
            conversationAuthorizationService.requireConversationMember(f.getConversation().getId(), viewer.getId());
        } else {
            marginAuthorizationService.requireMarginMember(viewer.getId(), f.getMargin().getId());
        }

        return serve(f.getStorageUrl(), fileName);
    }

    @GetMapping("/conversation-images/{fileName}")
    public ResponseEntity<Resource> getConversationImage(@PathVariable String fileName,
                                                         @AuthenticationPrincipal User viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StoredFile file = storedFileService.findByConversationImageFileName(fileName);
        if (file.getMessageId() == null || file.getChannel() == null) {
            return ResponseEntity.notFound().build();
        }
        conversationAuthorizationService.requireConversationMember(file.getChannel().getConversation().getId(), viewer.getId());
        return serve(file.getStorageUrl(), fileName);
    }

    private ResponseEntity<Resource> serve(String storageUrl, String fileName) {
        if ("s3".equals(storageProperties.getType())) {
            Optional<String> presigned = storageService.presign(storageUrl);
            if (presigned.isPresent()) {
                long ttl = storageProperties.getS3().getPresignTtlSeconds();
                long browserCache = Math.max(ttl - 60, 30);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=" + browserCache)
                        .location(URI.create(presigned.get()))
                        .build();
            }
        }

        try {
            Resource resource = storageService.getFile(storageUrl);
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
