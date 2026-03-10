package org.margin.server.websocket.models.payloads;

import org.margin.server.notifications.NotificationType;
import org.margin.server.users.models.dtos.UserDTO;

public record NotificationPayload(
        Long notificationId,
        NotificationType type,
        Long referenceId,
        Long marginId,
        UserDTO sender
) {}