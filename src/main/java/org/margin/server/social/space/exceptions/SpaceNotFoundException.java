package org.margin.server.social.space.exceptions;

public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException(Long spaceId) {
        super("User not found: " + spaceId);
    }
}
