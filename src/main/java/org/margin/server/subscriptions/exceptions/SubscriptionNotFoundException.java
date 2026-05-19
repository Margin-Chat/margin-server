package org.margin.server.subscriptions.exceptions;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(Long marginId) {
        super("No subscription found for margin " + marginId);
    }
}
