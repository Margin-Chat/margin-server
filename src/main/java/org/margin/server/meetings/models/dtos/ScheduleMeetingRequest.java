package org.margin.server.meetings.models.dtos;

import java.time.Instant;
import java.util.List;

public record ScheduleMeetingRequest(
        Long marginId,
        String title,
        Instant scheduledAt,
        Integer durationMinutes,
        String organizerTimezone,
        Boolean requireAdmission,
        List<InviteeRequest> invitees
) {
    public record InviteeRequest(Long userId, String email) {
    }
}
