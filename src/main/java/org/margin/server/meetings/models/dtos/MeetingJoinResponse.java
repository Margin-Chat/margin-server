package org.margin.server.meetings.models.dtos;

public record MeetingJoinResponse(String sfuUrl, String roomId, String roomToken, Integer maxVideoHeight) {
}
