package org.margin.server.meetings.models.dtos;

import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.ParticipantState;

public record MeetingParticipantDTO(
        Long userId,
        String displayName,
        boolean guest,
        MeetingRole role,
        ParticipantState state,
        boolean connected
) {
}
