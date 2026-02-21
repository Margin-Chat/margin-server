package org.margin.server.social.margin;

public class MarginNotFoundException extends RuntimeException {
    public MarginNotFoundException(Long id) {
        super("Margin not found: " + id);
    }
}