package org.margin.server.social.space.exceptions;

public class UserNotInMargin extends RuntimeException {
    public UserNotInMargin(Long userId) {
        super(String.format("User %d attempted to be added to a channel but is not part of the margin", userId));
    }
}
