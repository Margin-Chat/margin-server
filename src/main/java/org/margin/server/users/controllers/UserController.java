package org.margin.server.users.controllers;

import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.models.dtos.KeyUploadRequest;
import org.margin.server.users.models.dtos.PrivateKeyResponse;
import org.margin.server.users.models.dtos.PublicKeyResponse;

import java.util.List;

@RestController
@RequestMapping("api/users")
public class UserController {

    private final ConnectionManager connectionManager;
    private final UserService userService;
    private final ConversationService conversationService;
    private final StorageService storageService;

    public UserController(ConnectionManager connectionManager,
                          UserService userService,
                          ConversationService conversationService, StorageService storageService) {
        this.connectionManager = connectionManager;
        this.userService = userService;
        this.conversationService = conversationService;
        this.storageService = storageService;
    }

    @GetMapping("get_all_users")
    public List<UserDTO> getAllOnlineUsersOnServer(@AuthenticationPrincipal User user) {
        return connectionManager.getOnlineUserIds()
                .stream()
                .map(userService::getById)
                .filter(u -> !u.getId().equals(user.getId()))
                .map(UserDTO::new)
                .toList();
    }

    @GetMapping("/me")
    public UserDTO getCurrentUser(@AuthenticationPrincipal User user) {
        return new UserDTO(user);
    }

    @PostMapping("/{userId}/keys")
    public ResponseEntity<Void> uploadKeys(
            @PathVariable Long userId,
            @RequestBody KeyUploadRequest request,
            @AuthenticationPrincipal User authenticatedUser) {
        if (!authenticatedUser.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        userService.savePublicPrivateKeysForUser(userId, request.getPublicKey(), request.getEncryptedPrivateKey());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/public-key")
    public PublicKeyResponse getPublicKey(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return new PublicKeyResponse(user.getId(), user.getEncryption().getPublicKey());
    }

    @GetMapping("/me/private-key")
    public ResponseEntity<PrivateKeyResponse> getEncryptedPrivateKey(
            @AuthenticationPrincipal User user) {

        if (user.getEncryption().getEncryptedPrivateKey() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new PrivateKeyResponse(user.getEncryption().getEncryptedPrivateKey()));
    }

    @GetMapping("/{userId}/recent_chat_users")
    public List<UserDTO> getRecentChatUsers(@AuthenticationPrincipal User user) {
        return conversationService.getRecentChatUsers(user.getId());
    }

    @GetMapping("/search")
    public List<UserDTO> searchForUser(@RequestParam String query) {
        return userService.searchForUser(query);
    }
}