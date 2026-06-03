package org.margin.server.users.controllers;

import org.margin.server.bugs.services.BugReportService;
import org.margin.server.config.ratelimit.RateLimitConfig;
import org.margin.server.config.ratelimit.RateLimitService;
import org.margin.server.exceptions.TooManyRequestsException;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.*;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ConnectionManager connectionManager;
    private final UserService userService;
    private final ConversationService conversationService;
    private final MarginService marginService;
    private final RateLimitService rateLimitService;
    private final BugReportService bugReportService;

    public UserController(ConnectionManager connectionManager,
                          UserService userService,
                          ConversationService conversationService, MarginService marginService, RateLimitService rateLimitService, BugReportService bugReportService) {
        this.connectionManager = connectionManager;
        this.userService = userService;
        this.conversationService = conversationService;
        this.marginService = marginService;
        this.rateLimitService = rateLimitService;
        this.bugReportService = bugReportService;
    }

    @GetMapping("/{userId}")
    public UserDTO getUser(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return userService.toDTO(user);
    }

    @GetMapping("/get_all_users")
    public List<UserDTO> getAllOnlineUsersOnServer(@AuthenticationPrincipal User user) {
        return connectionManager.getOnlineUserIds()
                .stream()
                .map(userService::getById)
                .filter(u -> !u.getId().equals(user.getId()))
                .map(u -> new UserDTO(u, true))
                .toList();
    }

    @GetMapping("/me")
    public CurrentUserDTO getCurrentUser(@AuthenticationPrincipal User user) {
        return new CurrentUserDTO(user, connectionManager.isUserOnline(user.getId()));
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

        return ResponseEntity.ok(new PrivateKeyResponse(
                user.getEncryption().getEncryptedPrivateKey(),
                user.getEncryption().getSalt(),
                user.getEncryption().getIv()
        ));
    }

    @GetMapping("/recent_chat_users")
    public List<RecentChatUsersDTO> getRecentChatUsers(@AuthenticationPrincipal User user) {
        return conversationService.getRecentChatUsers(user.getId());
    }

    @GetMapping("/search_shared_margin")
    public List<UserSearchResultDTO> searchForUserWithSharedMargin(@AuthenticationPrincipal User user,
                                                                   @RequestParam String query) {
        return userService.searchUsersWithSharedMargins(user.getId(), query);
    }

    @GetMapping("/search_by_margin")
    public List<UserDTO> searchForUserByMargin(@AuthenticationPrincipal User user,
                                               @RequestParam Long marginId,
                                               @RequestParam String query) {
        return userService.searchUsersByMarginId(user.getId(), marginId, query);
    }

    @GetMapping("/lookup")
    public ResponseEntity<UserDTO> lookupByEmail(@AuthenticationPrincipal User requester,
                                                 @RequestParam String email) {
        String key = "user_lookup:" + requester.getId();
        if (!rateLimitService.tryConsume(key, RateLimitConfig.createMargin())) {
            throw new TooManyRequestsException("Too many lookups. Try again later.");
        }
        User user = userService.getByEmail(email);
        return ResponseEntity.ok(userService.toDTO(user));
    }

    @PatchMapping("/update_user_info")
    public CurrentUserDTO updateUserInfo(
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        if (file != null && !file.isEmpty()) {
            String key = "update_user_avatar:" + user.getId();
            if (!rateLimitService.tryConsume(key, RateLimitConfig.createMargin())) {
                throw new TooManyRequestsException("You can only update user avatar three times an hour.");
            }
        }
        User updatedUser = userService.updateUser(displayName, email, user, file);
        return new CurrentUserDTO(updatedUser, connectionManager.isUserOnline(updatedUser.getId()));
    }

    @PostMapping("/report_bug")
    public ResponseEntity<Void> reportBug(@RequestBody BugReportRequest request,
                                          @AuthenticationPrincipal User user) {
        bugReportService.createBug(request.bugTitle(), request.bugDescription(), user);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User user) {
        userService.changePassword(
                user,
                request.currentPassword(),
                request.newPassword(),
                request.encryptedPrivateKey(),
                request.salt(),
                request.iv()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal User user) {
        List<Long> marginIds = marginService.getMarginsForUser(user).stream()
                .map(MarginDTO::marginId)
                .toList();
        userService.deleteUser(marginIds, user);
        return ResponseEntity.ok().build();
    }
}