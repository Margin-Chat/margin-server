package org.margin.server.users.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.presence.PresenceService;
import org.margin.server.shared.exceptions.TooManyRequestsException;
import org.margin.server.shared.ratelimit.RateLimitConfig;
import org.margin.server.shared.ratelimit.RateLimitService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.*;
import org.margin.server.users.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final PresenceService presenceService;
    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final UserLookup userLookup;

    public UserController(PresenceService presenceService,
                          UserService userService,
                          RateLimitService rateLimitService,
                              UserLookup userLookup) {
        this.presenceService = presenceService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
        this.userLookup = userLookup;
    }

    @GetMapping("/{userId}")
    public UserDTO getUser(@PathVariable Long userId) {
        User user = userService.getById(userId);
        return userService.toDTO(user);
    }

    @GetMapping("/get_all_users")
    public List<UserDTO> getAllOnlineUsersOnServer(@AuthenticationPrincipal AuthenticatedUser user) {
        return presenceService.getOnlineUserIds()
                .stream()
                .map(userService::getById)
                .filter(u -> !u.getId().equals(user.id()))
                .map(u -> new UserDTO(u, true))
                .toList();
    }

    @GetMapping("/me")
    public CurrentUserDTO getCurrentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserDTO(entityOf(user), presenceService.isUserOnline(user.id()));
    }

    @PostMapping("/{userId}/keys")
    public ResponseEntity<Void> uploadKeys(
            @PathVariable Long userId,
            @RequestBody KeyUploadRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        if (!authenticatedUser.id().equals(userId)) {
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
            @AuthenticationPrincipal AuthenticatedUser user) {

        User entity = entityOf(user);
        if (entity.getEncryption().getEncryptedPrivateKey() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new PrivateKeyResponse(
                entity.getEncryption().getEncryptedPrivateKey(),
                entity.getEncryption().getSalt(),
                entity.getEncryption().getIv()
        ));
    }

    @GetMapping("/search_shared_margin")
    public List<UserSearchResultDTO> searchForUserWithSharedMargin(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @RequestParam String query) {
        return userService.searchUsersWithSharedMargins(user.id(), query);
    }

    @GetMapping("/search_by_margin")
    public List<UserDTO> searchForUserByMargin(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestParam Long marginId,
                                               @RequestParam String query) {
        return userService.searchUsersByMarginId(user.id(), marginId, query);
    }

    @GetMapping("/lookup")
    public ResponseEntity<UserDTO> lookupByEmail(@AuthenticationPrincipal AuthenticatedUser requester,
                                                 @RequestParam String email) {
        String key = "user_lookup:" + requester.id();
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
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (file != null && !file.isEmpty()) {
            String key = "update_user_avatar:" + user.id();
            if (!rateLimitService.tryConsume(key, RateLimitConfig.createMargin())) {
                throw new TooManyRequestsException("You can only update user avatar three times an hour.");
            }
        }
        User updatedUser = userService.updateUser(displayName, email, entityOf(user), file);
        return new CurrentUserDTO(updatedUser, presenceService.isUserOnline(updatedUser.getId()));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        userService.changePassword(
                entityOf(user),
                request.currentPassword(),
                request.newPassword(),
                request.encryptedPrivateKey(),
                request.salt(),
                request.iv()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthenticatedUser user) {
        userService.deleteUser(entityOf(user));
        return ResponseEntity.ok().build();
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userLookup.findById(principal.id()).orElseThrow();
    }
}