package org.margin.server.meetings.events;

public record MeetingParticipantRemovedEvent(Long meetingId, Long guestUserId) {
}
