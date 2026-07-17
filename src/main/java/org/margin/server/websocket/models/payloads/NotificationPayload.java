package org.margin.server.websocket.models.payloads;

import org.margin.server.notifications.NotificationType;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record NotificationPayload(
        Long notificationId,
        NotificationType type,
        Long referenceId,
        Long marginId,
        Long conversationId,
        UserDTO sender,
        Instant createdAt,
        boolean seen
) {}
