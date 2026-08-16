package org.margin.server.authentication.exceptions;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token is not valid. Please log in again.");
    }
}
