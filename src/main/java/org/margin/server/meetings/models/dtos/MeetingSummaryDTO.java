package org.margin.server.meetings.models.dtos;

import org.margin.server.meetings.models.MeetingStatus;

import java.time.Instant;

public record MeetingSummaryDTO(
        Long id,
        String code,
        String title,
        MeetingStatus status,
        Long marginId,
        String marginName,
        Long hostUserId,
        Instant scheduledAt,
        Integer durationMinutes,
        String organizerTimezone,
        Instant startedAt,
        Instant endedAt,
        Instant expiresAt,
        int inviteeCount
) {
}
