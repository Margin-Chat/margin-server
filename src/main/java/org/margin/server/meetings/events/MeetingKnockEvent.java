package org.margin.server.meetings.events;

import java.util.List;

public record MeetingKnockEvent(Long meetingId, Long guestUserId, String displayName, List<Long> hostUserIds) {
}
