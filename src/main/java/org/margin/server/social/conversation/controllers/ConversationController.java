package org.margin.server.social.conversation.controllers;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.*;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.config.ratelimit.RateLimitConfig;
import org.margin.server.config.ratelimit.RateLimitService;
import org.margin.server.exceptions.TooManyRequestsException;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.margin.server.websocket.models.payloads.SentConversationInvitePayload;
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

    public ConversationController(MessageService messageService,
                                  ConversationService conversationService, UserService userService,
                                  ConversationAuthorizationService conversationAuthorizationService,
                                  MarginAuthorizationService marginAuthorizationService,
                                  RateLimitService rateLimitService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.userService = userService;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/conversations")
    public List<ConversationDTO> getUserConversations(@AuthenticationPrincipal User user) {
        return conversationService.getUserConversationsDTO(user.getId());
    }

    @GetMapping("/conversations/{otherUserId}/direct_messages")
    public GetConversationMessagesResponse getConversationMessagesForUser(
            @PathVariable Long otherUserId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        conversationAuthorizationService.requireRecipientNotSelf(user.getId(), otherUserId);

        Conversation conversation = conversationService.findDirectConversationBetweenUsers(
                user.getId(), otherUserId);

        if (conversation == null) {
            return new GetConversationMessagesResponse(List.of(), null);
        }

        if (!conversationService.isUserMember(conversation.getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this conversation");
        }

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.getId())
        );
    }

    @GetMapping("/conversations/{channelId}/channel_messages")
    public GetConversationMessagesResponse getConversationMessagesForChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {

        marginAuthorizationService.requireChannelMember(user.getId(), channelId);

        Conversation conversation = conversationService.getByChannelId(channelId);

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.getId())
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public GetConversationMessagesResponse getConversationMessages(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {

        conversationAuthorizationService.requireConversationMember(conversationId, user.getId());

        Conversation conversation = conversationService.getById(conversationId);

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation, limit, before),
                conversationService.getConversationDTO(conversation, user.getId())
        );
    }

    @PostMapping("/conversations/create_private")
    public ConversationDTO startNewPrivateConversation(@RequestBody CreatePrivateConversationRequest request,
                                                       @AuthenticationPrincipal User user) {
        User recipientUser = userService.getById(request.recipientUserId());
        Conversation existing = conversationService.findDirectConversationBetweenUsers(user.getId(), recipientUser.getId());
        Conversation directConversation = existing != null
                ? existing
                : conversationService.createNewDirectConversation(user, recipientUser, request.encrypted());
        messageService.sendMessage(user, request.encryptedContent(), directConversation, null);
        return conversationService.getConversationDTO(directConversation, user.getId());
    }

    @PostMapping("/conversations/group")
    public ConversationDTO createGroupConversation(
            @RequestBody CreateGroupConversationRequest request,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.createGroupConversation(
                user,
                request.memberEmails(),
                request.name(),
                request.encrypted()
        );

        return conversationService.getConversationDTO(conversation, user.getId());
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {

        conversationAuthorizationService.requireConversationMember(conversationId, user.getId());
        conversationService.updateLastRead(conversationId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/conversations/unread")
    public UnreadConversationsDTO getUnreadConversations(@AuthenticationPrincipal User user) {
        return conversationService.getUnreadConversations(user.getId());
    }

    @PostMapping("/conversations/invite")
    public DirectConversationDTO sendConversationInvite(
            @RequestBody SendConversationInviteRequest request,
            @AuthenticationPrincipal User user) {
        String key = "conversation_invite:" + user.getId();
        if (!rateLimitService.tryConsume(key, RateLimitConfig.sendInvite())) {
            throw new TooManyRequestsException("Too many invites. Try again later.");
        }
        User recipient = userService.getByEmail(request.email());
        return conversationService.sendConversationInvite(user, recipient, request.isEncrypted());
    }

    @GetMapping("/conversations/pending_invites")
    public List<ConversationInvitePayload> getPendingInvites(@AuthenticationPrincipal User user) {
        return conversationService.getPendingInvites(user.getId());
    }

    @GetMapping("/conversations/sent_invites")
    public List<SentConversationInvitePayload> getSentInvites(@AuthenticationPrincipal User user) {
        return conversationService.getSentInvites(user.getId());
    }

    @PostMapping("/conversations/{conversationId}/invite/accept")
    public ResponseEntity<Void> acceptConversationInvite(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        conversationService.acceptConversationInvite(conversationId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/conversations/{conversationId}/invite/decline")
    public ResponseEntity<Void> declineConversationInvite(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        conversationService.declineConversationInvite(conversationId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{conversationId}/members")
    public ResponseEntity<Void> addMemberToConversation(
            @PathVariable Long conversationId,
            @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.getById(conversationId);

        conversationAuthorizationService.requireConversationTypeGroup(conversation);

        if (request.userId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself to a group");
        }

        conversationAuthorizationService.requireConversationMember(conversationId, user.getId());

        conversationService.addMember(conversationId, request.userId(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/conversations/{conversationId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromConversation(
            @PathVariable Long conversationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.getById(conversationId);

        conversationAuthorizationService.requireConversationTypeGroup(conversation);

        if (!userId.equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove other users");
        }

        conversationService.removeMember(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{conversationId}/member-public-keys")
    public Map<Long, String> getMemberPublicKeys(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {
        return conversationService.getMemberPublicKeys(conversationId, user.getId());
    }
}
