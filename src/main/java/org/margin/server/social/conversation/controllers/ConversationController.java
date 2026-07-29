package org.margin.server.social.conversation.controllers;

import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.*;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.shared.ratelimit.RateLimitConfig;
import org.margin.server.shared.ratelimit.RateLimitService;
import org.margin.server.shared.exceptions.TooManyRequestsException;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.social.conversation.models.dtos.ConversationInvitePayload;
import org.margin.server.social.conversation.models.dtos.SentConversationInvitePayload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UserService userService;
    private final ConversationAuthorizationService conversationAuthorizationService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final RateLimitService rateLimitService;
    private final UserLookup userLookup;

    public ConversationController(MessageService messageService,
                                  ConversationService conversationService, UserService userService,
                                  ConversationAuthorizationService conversationAuthorizationService,
                                  MarginAuthorizationService marginAuthorizationService,
                                  RateLimitService rateLimitService,
                              UserLookup userLookup) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.userService = userService;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.rateLimitService = rateLimitService;
        this.userLookup = userLookup;
    }

    @GetMapping("/conversations")
    public List<ConversationDTO> getUserConversations(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getUserConversationsDTO(user.id());
    }

    @GetMapping("/conversations/{otherUserId}/direct_messages")
    public GetConversationMessagesResponse getConversationMessagesForUser(
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        conversationAuthorizationService.requireRecipientNotSelf(user.id(), otherUserId);

        Conversation conversation = conversationService.findDirectConversationBetweenUsers(
                user.id(), otherUserId);

        if (conversation == null) {
            return new GetConversationMessagesResponse(List.of(), null);
        }

        if (!conversationService.isUserMember(conversation.getId(), user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this conversation");
        }

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.id())
        );
    }

    @GetMapping("/conversations/{channelId}/channel_messages")
    public GetConversationMessagesResponse getConversationMessagesForChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {

        marginAuthorizationService.requireChannelMember(user.id(), channelId);

        Conversation conversation = conversationService.getByChannelId(channelId);

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.id())
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public GetConversationMessagesResponse getConversationMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {

        conversationAuthorizationService.requireConversationMember(conversationId, user.id());

        Conversation conversation = conversationService.getById(conversationId);

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.id())
        );
    }

    @PostMapping("/conversations/create_private")
    public ConversationDTO startNewPrivateConversation(@RequestBody CreatePrivateConversationRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser user) {
        User recipientUser = userService.getById(request.recipientUserId());
        Conversation existing = conversationService.findDirectConversationBetweenUsers(user.id(), recipientUser.getId());
        Conversation directConversation = existing != null
                ? existing
                : conversationService.createNewDirectConversation(entityOf(user), recipientUser, request.encrypted());
        messageService.sendMessage(user.id(), request.encryptedContent(), directConversation.getId(), null);
        return conversationService.getConversationDTO(directConversation, user.id());
    }

    @PostMapping("/conversations/group")
    public ConversationDTO createGroupConversation(
            @RequestBody CreateGroupConversationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Conversation conversation = conversationService.createGroupConversation(
                entityOf(user),
                request.memberEmails(),
                request.name(),
                request.encrypted()
        );

        return conversationService.getConversationDTO(conversation, user.id());
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        conversationAuthorizationService.requireConversationMember(conversationId, user.id());
        conversationService.updateLastRead(conversationId, user.id());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/conversations/unread")
    public UnreadConversationsDTO getUnreadConversations(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getUnreadConversations(user.id());
    }

    @PostMapping("/conversations/invite")
    public DirectConversationDTO sendConversationInvite(
            @RequestBody SendConversationInviteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String key = "conversation_invite:" + user.id();
        if (!rateLimitService.tryConsume(key, RateLimitConfig.sendInvite())) {
            throw new TooManyRequestsException("Too many invites. Try again later.");
        }
        User recipient = userService.getByEmail(request.email());
        return conversationService.sendConversationInvite(entityOf(user), recipient, request.isEncrypted());
    }

    @GetMapping("/conversations/pending_invites")
    public List<ConversationInvitePayload> getPendingInvites(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getPendingInvites(user.id());
    }

    @GetMapping("/conversations/sent_invites")
    public List<SentConversationInvitePayload> getSentInvites(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getSentInvites(user.id());
    }

    @PostMapping("/conversations/{conversationId}/invite/accept")
    public ResponseEntity<Void> acceptConversationInvite(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        conversationService.acceptConversationInvite(conversationId, entityOf(user));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/conversations/{conversationId}/invite/decline")
    public ResponseEntity<Void> declineConversationInvite(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        conversationService.declineConversationInvite(conversationId, entityOf(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{conversationId}/members")
    public ResponseEntity<Void> addMemberToConversation(
            @PathVariable Long conversationId,
            @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Conversation conversation = conversationService.getById(conversationId);

        conversationAuthorizationService.requireConversationTypeGroup(conversation);

        if (request.userId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself to a group");
        }

        conversationAuthorizationService.requireConversationMember(conversationId, user.id());

        conversationService.addMember(conversationId, request.userId(), entityOf(user));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/conversations/{conversationId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromConversation(
            @PathVariable Long conversationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Conversation conversation = conversationService.getById(conversationId);

        conversationAuthorizationService.requireConversationTypeGroup(conversation);

        if (!userId.equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove other users");
        }

        conversationService.removeMember(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{conversationId}/member-public-keys")
    public Map<Long, String> getMemberPublicKeys(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getMemberPublicKeys(conversationId, user.id());
    }

    @GetMapping("/users/recent_chat_users")
    public List<RecentChatUsersDTO> getRecentChatUsers(@AuthenticationPrincipal AuthenticatedUser user) {
        return conversationService.getRecentChatUsers(user.id());
    }

    private User entityOf(AuthenticatedUser principal) {
        return principal == null ? null : userLookup.findById(principal.id()).orElseThrow();
    }
}
