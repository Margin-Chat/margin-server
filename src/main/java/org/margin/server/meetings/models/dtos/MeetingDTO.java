package org.margin.server.meetings.models.dtos;

import org.margin.server.meetings.models.MeetingStatus;

import java.time.Instant;
import java.util.List;

public record MeetingDTO(
        Long id,
        String code,
        String title,
        MeetingStatus status,
        Long marginId,
        String marginName,
        Long hostUserId,
        boolean requireAdmission,
        int maxParticipants,
        Instant scheduledAt,
        Integer durationMinutes,
        Instant startedAt,
        Instant endedAt,
        List<MeetingParticipantDTO> participants
) {
}
