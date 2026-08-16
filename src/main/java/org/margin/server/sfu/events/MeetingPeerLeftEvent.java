package org.margin.server.sfu.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MeetingPeerLeftEvent extends ApplicationEvent {
    private final String meetingCode;
    private final String peerId;

    public MeetingPeerLeftEvent(String meetingCode, String peerId) {
        super(meetingCode);
        this.meetingCode = meetingCode;
        this.peerId = peerId;
    }
}
