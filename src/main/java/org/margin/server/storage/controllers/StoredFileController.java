package org.margin.server.storage.controllers;

import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.MarginLookup;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.storage.StorageProperties;
import org.margin.server.storage.StorageUtils;
import org.margin.server.storage.dtos.StoredFileDTO;
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
    private final ConversationAuthorizationService conversationAuthorizationService;
    private final MarginAccessChecker marginAccessChecker;
    private final MarginLookup marginLookup;

    public StoredFileController(StorageService storageService,
                                StorageProperties storageProperties,
                                StoredFileService storedFileService,
                                ConversationAuthorizationService conversationAuthorizationService,
                                MarginAccessChecker marginAccessChecker,
                                MarginLookup marginLookup) {
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.storedFileService = storedFileService;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.marginAccessChecker = marginAccessChecker;
        this.marginLookup = marginLookup;
    }

    @GetMapping("/margins/{marginId}/stored-files")
    public ResponseEntity<List<StoredFileDTO>> listMarginFiles(@PathVariable Long marginId,
                                                               @AuthenticationPrincipal User viewer) {
        marginAccessChecker.requireMarginMember(viewer.getId(), marginId);

        return ResponseEntity.ok(storedFileService.toDTOs(storedFileService.findMarginFiles(marginId)));
    }

    @GetMapping("/channels/{channelId}/stored-files")
    public ResponseEntity<List<StoredFileDTO>> listChannelFiles(@PathVariable Long channelId,
                                                                @AuthenticationPrincipal User viewer) {
        conversationAuthorizationService.requireConversationMemberForChannel(channelId, viewer.getId());

        return ResponseEntity.ok(storedFileService.toDTOs(storedFileService.findChannelFiles(channelId)));
    }

    @PostMapping("/margins/{marginId}/stored-files")
    public ResponseEntity<StoredFileDTO> uploadMarginFile(@PathVariable Long marginId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @AuthenticationPrincipal User uploader) {
        marginAccessChecker.requireMarginAdmin(uploader.getId(), marginId);
        StoredFile saved = storedFileService.uploadMarginFile(marginId, file, uploader);
        return ResponseEntity.ok(StoredFileDTO.from(saved, uploader, false));
    }

    @PostMapping("/channels/{channelId}/stored-files")
    public ResponseEntity<StoredFileDTO> uploadChannelFile(@PathVariable Long channelId,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                                                           @AuthenticationPrincipal User uploader) {
        marginAccessChecker.requireChannelMember(uploader.getId(), channelId);
        StoredFile saved = storedFileService.uploadChannelFile(channelId, file, uploader, inline);
        return ResponseEntity.ok(StoredFileDTO.from(saved, uploader, false));
    }

    @PostMapping("/conversations/{conversationId}/stored-files")
    public ResponseEntity<StoredFileDTO> uploadConversationFile(@PathVariable Long conversationId,
                                                                @RequestParam("file") MultipartFile file,
                                                                @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                                                                @AuthenticationPrincipal User uploader) {
        StorageUtils.requireConversationFileSize(file);
        conversationAuthorizationService.requireConversationMember(conversationId, uploader.getId());
        StoredFile saved = storedFileService.uploadConversationFile(conversationId, file, uploader, inline);
        return ResponseEntity.ok(StoredFileDTO.from(saved, uploader, false));
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

        if (!f.getUploadedByUserId().equals(actor.getId())) {
            marginAccessChecker.requireMarginAdmin(actor.getId(), f.getMarginId());
        }

        f.setFileName(newName);
        StoredFile saved = storedFileService.save(f);
        return ResponseEntity.ok(StoredFileDTO.from(saved, actor, false));
    }

    public record RenameRequest(String fileName) {
    }

    @DeleteMapping("/stored-files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId,
                                           @AuthenticationPrincipal User actor) {
        StoredFile f = storedFileService.getById(fileId);

        if (!f.getUploadedByUserId().equals(actor.getId())) {
            marginAccessChecker.requireMarginAdmin(actor.getId(), f.getMarginId());
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
            marginAccessChecker.requireMarginMember(viewer.getId(), f.getMarginId());
        } else if (f.getScope() == StoredFileScope.CHANNEL) {
            marginAccessChecker.requireChannelMember(viewer.getId(), f.getChannelId());
        } else {
            conversationAuthorizationService.requireConversationMember(f.getConversationId(), viewer.getId());
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
