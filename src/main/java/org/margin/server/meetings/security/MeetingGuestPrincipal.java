package org.margin.server.meetings.security;

public record MeetingGuestPrincipal(Long userId, Long meetingId, String meetingCode, String displayName) {
}
