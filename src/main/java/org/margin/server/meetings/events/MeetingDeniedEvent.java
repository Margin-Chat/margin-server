package org.margin.server.meetings.events;

public record MeetingDeniedEvent(Long meetingId, Long guestUserId) {
}
