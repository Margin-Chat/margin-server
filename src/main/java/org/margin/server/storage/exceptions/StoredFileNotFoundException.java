package org.margin.server.storage.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class StoredFileNotFoundException extends ResponseStatusException {
    public StoredFileNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Stored file not found: " + id);
    }

    public StoredFileNotFoundException(String fileName) {
        super(HttpStatus.NOT_FOUND, "Stored file not found: " + fileName);
    }
}