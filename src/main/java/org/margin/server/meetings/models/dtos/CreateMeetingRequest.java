package org.margin.server.meetings.models.dtos;

public record CreateMeetingRequest(Long marginId, String title, Boolean requireAdmission) {
}
