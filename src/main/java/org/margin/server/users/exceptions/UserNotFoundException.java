package org.margin.server.users.exceptions;

import org.springframework.modulith.NamedInterface;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

@NamedInterface("api")
public class UserNotFoundException extends DomainException {
    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "User not found");
    }
}
