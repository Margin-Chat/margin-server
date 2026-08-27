package org.margin.server.meetings.api;

public interface MeetingAdmissionCommands {

    void knock(Long meetingId, Long guestUserId);

    void leaveLobby(Long meetingId, Long guestUserId);
}
