package org.margin.server.websocket.processors;

import org.margin.server.meetings.api.MeetingAdmissionCommands;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class MeetingInviteProcessor implements WebSocketMessageProcessor<String> {

    private final MeetingAdmissionCommands admissionCommands;

    public MeetingInviteProcessor(MeetingAdmissionCommands admissionCommands) {
        this.admissionCommands = admissionCommands;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.MEETING_INVITE;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<String> message) {
        admissionCommands.ring(message.getPayload(), user.id(), message.getRecipientId());
    }
}
