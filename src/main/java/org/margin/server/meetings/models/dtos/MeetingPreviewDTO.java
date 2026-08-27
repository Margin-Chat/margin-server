package org.margin.server.meetings.models.dtos;

public record MeetingPreviewDTO(
        String code,
        String title,
        String marginName,
        String hostDisplayName,
        boolean requireAdmission,
        boolean joinable
) {
}
