package org.margin.server.storage.controllers;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.api.ChannelLookup;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.storage.services.StorageService;
import org.margin.server.storage.services.StoredFileService;
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
    private final MarginAccessChecker marginAccessChecker;
    private final ConversationAuthorizationService conversationAuthorizationService;
    private final ChannelLookup channelLookup;

    public FilesController(StorageService storageService,
                           StorageProperties storageProperties,
                           MarginLookup marginLookup,
                           StoredFileService storedFileService,
                           MarginAccessChecker marginAccessChecker,
                           ConversationAuthorizationService conversationAuthorizationService,
                           ChannelLookup channelLookup) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.marginLookup = marginLookup;
        this.storedFileService = storedFileService;
        this.marginAccessChecker = marginAccessChecker;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.channelLookup = channelLookup;
    }

    @GetMapping("/user-profiles/{fileName}")
    public ResponseEntity<Resource> getProfilePicture(@PathVariable String fileName,
                                                      @AuthenticationPrincipal AuthenticatedUser viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return serve("/user-profiles/" + fileName, fileName);
    }

    @GetMapping("/margin-icons/{fileName}")
    public ResponseEntity<Resource> getMarginIcon(@PathVariable String fileName,
                                                  @AuthenticationPrincipal AuthenticatedUser viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        MarginLookup.MarginIcon icon = marginLookup.iconByFileName(fileName);
        marginAccessChecker.requireMarginMember(viewer.id(), icon.marginId());
        return serve(icon.iconUrl(), fileName);
    }

    @GetMapping("/stored-files/{fileName}")
    public ResponseEntity<Resource> getStoredFileByName(@PathVariable String fileName,
                                                        @AuthenticationPrincipal AuthenticatedUser viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StoredFile f = storedFileService.findByStoredFileName(fileName);

        if (f.getScope() == StoredFileScope.CHANNEL) {
            conversationAuthorizationService.requireConversationMember(channelLookup.conversationIdOf(f.getChannelId()), viewer.id());
        } else if (f.getScope() == StoredFileScope.CONVERSATION) {
            conversationAuthorizationService.requireConversationMember(f.getConversationId(), viewer.id());
        } else {
            marginAccessChecker.requireMarginMember(viewer.id(), f.getMarginId());
        }

        return serve(f.getStorageUrl(), fileName);
    }

    @GetMapping("/conversation-images/{fileName}")
    public ResponseEntity<Resource> getConversationImage(@PathVariable String fileName,
                                                         @AuthenticationPrincipal AuthenticatedUser viewer) {
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        StoredFile file = storedFileService.findByConversationImageFileName(fileName);
        if (file.getMessageId() == null || file.getChannelId() == null) {
            return ResponseEntity.notFound().build();
        }
        conversationAuthorizationService.requireConversationMember(channelLookup.conversationIdOf(file.getChannelId()), viewer.id());
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
