package org.margin.server.notifications.push;

public interface PushSender {

    class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    PushPlatform platform();

    boolean isEnabled();

    void send(String token, PushMessage message) throws InvalidTokenException;
}
