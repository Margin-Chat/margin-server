package org.margin.server.meetings.api;

import org.margin.server.meetings.security.MeetingGuestPrincipal;

import java.util.Optional;

public interface MeetingGuestTokens {

    Optional<MeetingGuestPrincipal> parse(String token);
}
