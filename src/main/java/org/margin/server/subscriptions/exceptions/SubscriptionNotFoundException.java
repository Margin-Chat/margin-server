package org.margin.server.subscriptions.exceptions;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

public class SubscriptionNotFoundException extends DomainException {
    public SubscriptionNotFoundException(Long marginId) {
        super(HttpStatus.NOT_FOUND, "No subscription found for margin " + marginId);
    }
}
