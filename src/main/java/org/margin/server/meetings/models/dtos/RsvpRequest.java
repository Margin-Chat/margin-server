package org.margin.server.meetings.models.dtos;

public record RsvpRequest(String inviteToken, boolean accepted) {
}
