package org.margin.server.storage.controllers;

import org.margin.server.social.channel.ChannelLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.StorageService;
import org.margin.server.storage.StoredFileService;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.storage.models.StoredFile;
import org.margin.server.storage.models.StoredFileScope;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.models.User;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
public class StoredFileController {
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final StoredFileService storedFileService;
    private final ChannelLookup channelLookup;
    private final ConversationAuthorizationService conversationAuthorizationService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final MarginLookup marginLookup;
    private final SubscriptionValidationService subscriptionValidationService;

    public StoredFileController(StorageService storageService,
                                StorageProperties storageProperties,
                                StoredFileService storedFileService,
                                ChannelLookup channelLookup,
                                ConversationAuthorizationService conversationAuthorizationService,
                                MarginAuthorizationService marginAuthorizationService,
                                MarginLookup marginLookup,
                                SubscriptionValidationService subscriptionValidationService) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.storedFileService = storedFileService;
        this.channelLookup = channelLookup;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.marginLookup = marginLookup;
        this.subscriptionValidationService = subscriptionValidationService;
    }

    @GetMapping("/margins/{marginId}/stored-files")
    public ResponseEntity<List<StoredFileDTO>> listMarginFiles(@PathVariable Long marginId,
                                                               @AuthenticationPrincipal User viewer) {
        marginAuthorizationService.requireMarginMember(viewer.getId(), marginId);

        List<StoredFileDTO> out = storedFileService.findMarginFiles(marginId).stream()
                .map(f -> StoredFileDTO.from(f, false))
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/channels/{channelId}/stored-files")
    public ResponseEntity<List<StoredFileDTO>> listChannelFiles(@PathVariable Long channelId,
                                                                @AuthenticationPrincipal User viewer) {
        conversationAuthorizationService.requireConversationMemberForChannel(channelId, viewer.getId());

        List<StoredFileDTO> out = storedFileService.findChannelFiles(channelId).stream()
                .map(f -> StoredFileDTO.from(f, false))
                .toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/margins/{marginId}/stored-files")
    public ResponseEntity<StoredFileDTO> uploadMarginFile(@PathVariable Long marginId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @AuthenticationPrincipal User uploader) {
        marginAuthorizationService.requireMarginAdmin(uploader.getId(), marginId);
        Margin margin = marginLookup.getById(marginId);
        subscriptionValidationService.validateStorageQuota(margin, file.getSize());

        String url = storageService.saveStoredFile(file);

        StoredFile entity = new StoredFile();
        entity.setScope(StoredFileScope.MARGIN);
        entity.setMargin(margin);
        entity.setChannel(null);
        entity.setFileName(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setStorageUrl(url);
        entity.setUploadedBy(uploader);

        StoredFile saved = storedFileService.save(entity);
        return ResponseEntity.ok(StoredFileDTO.from(saved, false));
    }

    @PostMapping("/channels/{channelId}/stored-files")
    public ResponseEntity<StoredFileDTO> uploadChannelFile(@PathVariable Long channelId,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                                                           @AuthenticationPrincipal User uploader) {
        marginAuthorizationService.requireChannelMember(uploader.getId(), channelId);

        Channel channel = channelLookup.getById(channelId);
        Margin margin = channel.getSpace().getMargin();

        subscriptionValidationService.validateStorageQuota(margin, file.getSize());
        String url = storageService.saveStoredFile(file);

        StoredFile entity = new StoredFile();
        entity.setScope(StoredFileScope.CHANNEL);
        entity.setMargin(margin);
        entity.setChannel(channel);
        entity.setFileName(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setStorageUrl(url);
        entity.setUploadedBy(uploader);
        entity.setInline(inline);

        StoredFile saved = storedFileService.save(entity);
        return ResponseEntity.ok(StoredFileDTO.from(saved, false));
    }

    @PatchMapping("/stored-files/{fileId}")
    public ResponseEntity<StoredFileDTO> renameFile(@PathVariable Long fileId,
                                                    @RequestBody RenameRequest body,
                                                    @AuthenticationPrincipal User actor) {
        if (body == null || body.fileName() == null || body.fileName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String newName = body.fileName().trim();
        if (newName.length() > 512) {
            return ResponseEntity.badRequest().build();
        }

        StoredFile f = storedFileService.getById(fileId);

        if (!f.getUploadedBy().getId().equals(actor.getId())) {
            marginAuthorizationService.requireMarginAdmin(actor.getId(), f.getMargin().getId());
        }

        f.setFileName(newName);
        StoredFile saved = storedFileService.save(f);
        return ResponseEntity.ok(StoredFileDTO.from(saved, false));
    }

    public record RenameRequest(String fileName) {
    }

    @DeleteMapping("/stored-files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId,
                                           @AuthenticationPrincipal User actor) {
        StoredFile f = storedFileService.getById(fileId);

        if (!f.getUploadedBy().getId().equals(actor.getId())) {
            marginAuthorizationService.requireMarginAdmin(actor.getId(), f.getMargin().getId());
        }

        f.setDeletedAt(Instant.now());
        storedFileService.save(f);

        storageService.delete(f.getStorageUrl());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stored-files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long fileId,
                                             @AuthenticationPrincipal User viewer) {
        StoredFile f = storedFileService.getById(fileId);

        if (f.getScope() == StoredFileScope.MARGIN) {
            marginAuthorizationService.requireMarginMember(viewer.getId(), f.getMargin().getId());
        } else {
            marginAuthorizationService.requireChannelMember(viewer.getId(), f.getChannel().getId());
        }

        if ("s3".equals(storageProperties.getType())) {
            Optional<String> presigned = storageService.presign(f.getStorageUrl(), f.getFileName());
            if (presigned.isPresent()) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .location(URI.create(presigned.get()))
                        .build();
            }
        }

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(f.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + f.getFileName().replace("\"", "") + "\"")
                    .body(storageService.getFile(f.getStorageUrl()));
        } catch (IOException _) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
