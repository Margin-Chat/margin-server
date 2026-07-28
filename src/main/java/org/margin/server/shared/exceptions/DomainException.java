package org.margin.server.shared.exceptions;

import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getProperties() {
        return Map.of();
    }
}
