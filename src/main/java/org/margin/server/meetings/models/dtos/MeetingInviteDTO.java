package org.margin.server.meetings.models.dtos;

import org.margin.server.meetings.models.MeetingInviteStatus;

public record MeetingInviteDTO(
        Long inviteId,
        Long userId,
        String displayName,
        String email,
        MeetingInviteStatus status
) {
}
