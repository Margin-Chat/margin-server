package org.margin.server.social.space.exceptions;

public class SpaceNotFoundException extends RuntimeException {
    private final Long spaceId;

    public SpaceNotFoundException(Long spaceId) {
        super("User not found: " + spaceId);
        this.spaceId = spaceId;
    }
}
