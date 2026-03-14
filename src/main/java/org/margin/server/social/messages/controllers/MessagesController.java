package org.margin.server.social.messages.controllers;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.dtos.*;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MessagesController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    public MessagesController(MessageService messageService,
                              ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
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

        if (otherUserId.equals(user.getId())) {
            throw new RuntimeException("Cannot get conversation with yourself");
        }

        Conversation conversation = conversationService.findDirectConversationBetweenUsers(
                user.getId(), otherUserId);

        if (conversation == null) {
            return new GetConversationMessagesResponse(List.of(), null);
        }

        if (!conversationService.isUserMember(conversation.getId(), user.getId())) {
            throw new RuntimeException("User is not a member of this conversation");
        }

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation.getId(), limit, before),
                conversationService.getConversationDTO(conversation, user.getId())
        );
    }

    @GetMapping("/conversations/{channelId}/channel_messages")
    public GetConversationMessagesResponse getConversationMessagesForChannel(
            @PathVariable Long channelId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {

        Conversation conversation = conversationService.getByChannelId(channelId);

        return new GetConversationMessagesResponse(
                messageService.getConversationMessages(conversation.getId(), limit, before),
                conversationService.getConversationDTO(conversation, user.getId())
        );
    }

    @PostMapping("/conversations/group")
    public ConversationDTO createGroupConversation(
            @RequestBody CreateGroupConversationRequest request,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.createGroupConversation(
                user,
                request.userIds(),
                request.name()
        );

        return conversationService.getConversationDTO(conversation, user.getId());
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markConversationAsRead(
            @PathVariable Long conversationId,
            @AuthenticationPrincipal User user) {

        if (!conversationService.isUserMember(conversationId, user.getId())) {
            throw new RuntimeException("User is not a member of this conversation");
        }

        conversationService.updateLastRead(conversationId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/conversations/unread")
    public List<UnreadCountDTO> getUnreadMessagesCount(@AuthenticationPrincipal User user) {
        return conversationService.getUnreadMessagesCounts(user.getId());
    }

    @PostMapping("/conversations/{conversationId}/members")
    public ResponseEntity<Void> addMemberToConversation(
            @PathVariable Long conversationId,
            @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.getById(conversationId);

        if (conversation.getType() != ConversationType.GROUP) {
            throw new RuntimeException("Can only add members to group conversations");
        }

        if (!conversationService.isUserMember(conversationId, user.getId())) {
            throw new RuntimeException("You are not a member of this conversation");
        }

        conversationService.addMember(conversationId, request.userId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/conversations/{conversationId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromConversation(
            @PathVariable Long conversationId,
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {

        Conversation conversation = conversationService.getById(conversationId);

        if (conversation.getType() != ConversationType.GROUP) {
            throw new RuntimeException("Can only remove members from group conversations");
        }

        if (!userId.equals(user.getId())) {
            // TODO: Check if user is admin/creator of the group
            throw new RuntimeException("Cannot remove other users");
        }

        conversationService.removeMember(conversationId, userId);
        return ResponseEntity.noContent().build();
    }
}