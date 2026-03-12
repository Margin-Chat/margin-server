package org.margin.server.users.controllers;

import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.users.models.dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/users")
public class UserController {

    private final ConnectionManager connectionManager;
    private final UserService userService;
    private final ConversationService conversationService;

    public UserController(ConnectionManager connectionManager,
                          UserService userService,
                          ConversationService conversationService) {
        this.connectionManager = connectionManager;
        this.userService = userService;
        this.conversationService = conversationService;
    }

    @GetMapping("get_all_users")
    public List<UserDTO> getAllOnlineUsersOnServer(@AuthenticationPrincipal User user) {
        return connectionManager.getOnlineUserIds()
                .stream()
                .map(userService::getById)
                .filter(u -> !u.getId().equals(user.getId()))
                .map(u -> new UserDTO(u, true))
                .toList();
    }

    @GetMapping("/me")
    public UserDTO getCurrentUser(@AuthenticationPrincipal User user) {
        return new UserDTO(user, connectionManager.isUserOnline(user.getId()));
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
    public List<RecentChatUsersDTO> getRecentChatUsers(@AuthenticationPrincipal User user) {
        return conversationService.getRecentChatUsers(user.getId());
    }

    @GetMapping("/search")
    public List<UserDTO> searchForUser(@RequestParam String query) {
        return userService.searchForUser(query);
    }


    @PatchMapping("me")
    public UserDTO getCurrentUser(
            @RequestParam String displayName,
            @RequestParam String email,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        User updatedUser = userService.updateUser(displayName, email, user, file);
        return new UserDTO(updatedUser, connectionManager.isUserOnline(updatedUser.getId()));
    }

}