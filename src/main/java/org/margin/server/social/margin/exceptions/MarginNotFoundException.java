package org.margin.server.social.margin.exceptions;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

public class MarginNotFoundException extends DomainException {
    public MarginNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Margin not found: " + id);
    }

    public MarginNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "Margin not found: " + detail);
    }
}
