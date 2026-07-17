package org.margin.server.notifications.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.margin.server.notifications.events.AnnouncementCreatedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.messages.events.MessageSentEvent;
import org.margin.server.users.models.User;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class PushEventListener {

    private static final int MAX_BODY_LENGTH = 140;

    private final PushDispatchService pushDispatchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushEventListener(PushDispatchService pushDispatchService) {
        this.pushDispatchService = pushDispatchService;
    }

    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        Long senderId = event.getMessage().user().id();
        String title = event.getMessage().channelName() != null
                ? "#" + event.getMessage().channelName()
                : event.getMessage().user().displayName();
        PushMessage push = new PushMessage(
                title,
                previewBody(event.getMessage().content()),
                Map.of(
                        "type", "message",
                        "conversationId", String.valueOf(event.getMessage().conversationId())
                )
        );
        event.getRecipients().stream()
                .map(User::getId)
                .filter(id -> !Objects.equals(id, senderId))
                .forEach(id -> pushDispatchService.pushToUserIfOffline(id, push));
    }

    @EventListener
    public void onConversationInvite(ConversationInviteEvent event) {
        String kind = "GROUP".equals(event.getConversation().type()) ? "a group chat" : "a private chat";
        PushMessage push = new PushMessage(
                event.getSender().getDisplayName(),
                "Invited you to " + kind,
                Map.of(
                        "type", "invite",
                        "conversationId", String.valueOf(event.getConversation().id())
                )
        );
        pushDispatchService.pushToUserIfOffline(event.getRecipientId(), push);
    }

    @EventListener
    public void onAnnouncementCreated(AnnouncementCreatedEvent event) {
        PushMessage push = new PushMessage(
                event.getAuthor().getDisplayName(),
                "Posted an announcement",
                Map.of(
                        "type", "announcement",
                        "marginId", String.valueOf(event.getMarginId())
                )
        );
        event.getMembers().stream()
                .map(User::getId)
                .filter(id -> !Objects.equals(id, event.getAuthor().getId()))
                .forEach(id -> pushDispatchService.pushToUserIfOffline(id, push));
    }

    private String previewBody(String content) {
        if (looksEncrypted(content)) {
            return "New message";
        }
        return content.length() <= MAX_BODY_LENGTH ? content : content.substring(0, MAX_BODY_LENGTH) + "…";
    }

    private boolean looksEncrypted(String content) {
        try {
            JsonNode node = objectMapper.readTree(Base64.getDecoder().decode(content));
            return node.isObject() && node.has("iv");
        } catch (Exception e) {
            return false;
        }
    }
}
