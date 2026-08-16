package org.margin.server.sfu.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MeetingPeerJoinedEvent extends ApplicationEvent {
    private final String meetingCode;
    private final String peerId;

    public MeetingPeerJoinedEvent(String meetingCode, String peerId) {
        super(meetingCode);
        this.meetingCode = meetingCode;
        this.peerId = peerId;
    }
}
