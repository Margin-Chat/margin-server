package org.margin.server.meetings.models.dtos;

import java.time.Instant;

public record RescheduleMeetingRequest(Instant scheduledAt, Integer durationMinutes, String title) {
}
