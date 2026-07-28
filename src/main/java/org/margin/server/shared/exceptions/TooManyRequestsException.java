package org.margin.server.shared.exceptions;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends DomainException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
