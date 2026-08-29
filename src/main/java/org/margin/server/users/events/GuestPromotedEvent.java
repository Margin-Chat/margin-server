package org.margin.server.users.events;

public record GuestPromotedEvent(Long userId, String email, String displayName) {
}
