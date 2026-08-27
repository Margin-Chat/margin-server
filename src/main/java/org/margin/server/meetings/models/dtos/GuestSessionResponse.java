package org.margin.server.meetings.models.dtos;

public record GuestSessionResponse(String guestToken, Long userId, String displayName, boolean requiresAdmission) {
}
