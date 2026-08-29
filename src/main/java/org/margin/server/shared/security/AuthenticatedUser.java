package org.margin.server.shared.security;

public record AuthenticatedUser(Long id, String email, String displayName) {
}
