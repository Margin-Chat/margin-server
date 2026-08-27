package org.margin.server.meetings.events;

public record MeetingAdmittedEvent(Long meetingId, Long guestUserId) {
}
