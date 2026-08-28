package org.margin.server.notifications.models.dtos;

import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record NotificationDTO(
        Long notificationId,
        NotificationType type,
        Long referenceId,
        Long marginId,
        Long conversationId,
        UserDTO sender,
        boolean seen,
        Instant createdAt
) {
}
