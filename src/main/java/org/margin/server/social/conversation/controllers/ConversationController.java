package org.margin.server.social.conversation.controllers;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.*;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.validations.ConversationAuthorizationService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UserService userService;
    private final ConversationAuthorizationService conversationAuthorizationService;
    private final MarginAuthorizationService marginAuthorizationService;

    public ConversationController(MessageService messageService,
                                  ConversationService conversationService, UserService userService,
                                  ConversationAuthorizationService conversationAuthorizationService,
                                  MarginAuthorizationService marginAuthorizationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.userService = userService;
        this.conversationAuthorizationService = conversationAuthorizationService;
        this.marginAuthorizationService = marginAuthorizationService;
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

    @PostMapping("/conversations/create_private")
    public ConversationDTO startNewPrivateConversation(@RequestBody CreatePrivateConversationRequest request,
                                                       @AuthenticationPrincipal User user) {
        User recipientUser = userService.getById(request.recipientUserId());
        Conversation existing = conversationService.findDirectConversationBetweenUsers(user.getId(), recipientUser.getId());
        Conversation directConversation = existing != null
                ? existing
                : conversationService.createNewDirectConversation(user, recipientUser);
        messageService.sendMessage(user, request.encryptedContent(), directConversation);
        return conversationService.getConversationDTO(directConversation, user.getId());
    }

    @PostMapping("/conversations/group")
    public ConversationDTO createGroupConversation(
            @RequestBody CreateGroupConversationRequest request,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.createGroupConversation(
                request.userIds(),
                request.name()
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
        User recipient = userService.getByHandle(request.handle());
        return conversationService.sendConversationInvite(user, recipient);
    }

    @GetMapping("/conversations/pending_invites")
    public List<ConversationInvitePayload> getPendingInvites(@AuthenticationPrincipal User user) {
        return conversationService.getPendingInvites(user.getId());
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

        conversationAuthorizationService.requireConversationMember(conversationId, user.getId());

        conversationService.addMember(conversationId, request.userId());
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
}