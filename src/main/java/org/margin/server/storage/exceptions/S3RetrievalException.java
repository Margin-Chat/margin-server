package org.margin.server.storage.exceptions;

public class S3RetrievalException extends RuntimeException {
    public S3RetrievalException(Throwable cause) {
        super("Failed to retrieve file from S3", cause);
    }
}