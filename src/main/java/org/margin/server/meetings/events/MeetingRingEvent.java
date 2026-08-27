package org.margin.server.meetings.events;

import org.margin.server.meetings.models.MeetingInvitePayload;

public record MeetingRingEvent(Long recipientId, MeetingInvitePayload payload) {
}
