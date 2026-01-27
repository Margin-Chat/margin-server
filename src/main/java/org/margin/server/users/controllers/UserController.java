package org.margin.server.users.controllers;

import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserDTO;
import org.margin.server.users.models.keys.KeyUploadRequest;
import org.margin.server.users.models.keys.PrivateKeyResponse;
import org.margin.server.users.models.keys.PublicKeyResponse;
import org.margin.server.websocket.services.WebSocketClientService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
	private final WebSocketClientService webSocketClientService;
	private final UserService userService;
    private final DirectChatMessageRepository directChatMessageRepository;

	public UserController(WebSocketClientService webSocketClientService,
                          UserService userService,
                          DirectChatMessageRepository directChatMessageRepository,
                          UserRepository userRepository) {
		this.webSocketClientService = webSocketClientService;
		this.userService = userService;
        this.directChatMessageRepository = directChatMessageRepository;
        this.userRepository = userRepository;
	}

	@GetMapping("get_all_users")
	public List<UserDTO> getAllOnlineUsersOnServer(Authentication authentication) {
        String username = authentication.getName();
		return webSocketClientService.getAllClients()
                .keySet()
                .stream()
                .map(userService::getById)
                .filter(user -> !user.getUsername().equals(username))
                .map(UserDTO::new)
                .toList();
	}

    @GetMapping("/me")
    public UserDTO getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return new UserDTO(userService.getByUsername(username));
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
        return new PublicKeyResponse(user.getId(), user.getPublicKey());
    }

    @GetMapping("/me/private-key")
    public ResponseEntity<PrivateKeyResponse> getEncryptedPrivateKey(
            @AuthenticationPrincipal User user) {

        if (user.getEncryptedPrivateKey() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new PrivateKeyResponse(user.getEncryptedPrivateKey()));
    }

    @GetMapping("/{userId}/recent_chat_users")
    public List<UserDTO> getRecentChatUsers(@PathVariable Long userId) {
        List<Long> recentUserIds = directChatMessageRepository.findRecentChatUserIds(userId).stream()
                .distinct()
                .collect(Collectors.toList());
        return userRepository.findAllByIdIn(recentUserIds).stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    @GetMapping("/search")
    public List<UserDTO> searchForUser(@RequestParam String query) {
        return userService.searchForUser(query);
    }
}
