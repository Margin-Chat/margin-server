package org.margin.server.notifications.push;

/** One per platform (FCM, APNs). Disabled senders (missing credentials) are skipped. */
public interface PushSender {

    /** Thrown when the platform reports the token as dead — the registry entry gets deleted. */
    class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    PushPlatform platform();

    boolean isEnabled();

    void send(String token, PushMessage message) throws InvalidTokenException;
}
