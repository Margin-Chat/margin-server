package org.margin.server.meetings.models;

import org.margin.server.users.models.dtos.UserDTO;

public record MeetingInvitePayload(String code, String title, String marginName, UserDTO inviter) {
}
