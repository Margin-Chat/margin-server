package org.margin.server.social.margin.exceptions;

public class MarginNotFoundException extends RuntimeException {
    public MarginNotFoundException(Long id) {
        super("Margin not found: " + id);
    }

    public MarginNotFoundException(String detail) {
        super("Margin not found: " + detail);
    }
}