package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.users.models.User;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;

class WebSocketMessageBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebSocketMessageBuilder builder = new WebSocketMessageBuilder(mapper);

    @Test
    void notificationFrame_carriesConversationIdAndReadState() {
        User recipient = createUser(1L);
        User sender = createUser(2L, "bob");

        Notification notification = new Notification();
        notification.setNotificationId(56L);
        notification.setRecipient(recipient);
        notification.setSender(sender);
        notification.setType(NotificationType.THREAD_REPLY);
        notification.setReferenceId(258L);
        notification.setMarginId(1L);
        notification.setConversationId(77L);
        notification.setSeen(false);
        notification.setCreatedAt(Instant.parse("2026-07-12T10:00:00Z"));

        JsonNode frame = mapper.readTree(builder.notification(notification));
        JsonNode payload = frame.get("payload");

        assertThat(frame.get("type").asString()).isEqualTo("NOTIFICATION");
        assertThat(payload.get("notificationId").asLong()).isEqualTo(56L);
        assertThat(payload.get("type").asString()).isEqualTo("THREAD_REPLY");
        assertThat(payload.get("referenceId").asLong()).isEqualTo(258L);
        assertThat(payload.get("marginId").asLong()).isEqualTo(1L);
        assertThat(payload.get("conversationId").asLong()).isEqualTo(77L);
        assertThat(payload.get("seen").asBoolean()).isFalse();
        assertThat(payload.hasNonNull("createdAt")).isTrue();
        assertThat(payload.get("sender").get("id").asLong()).isEqualTo(2L);
    }
}
