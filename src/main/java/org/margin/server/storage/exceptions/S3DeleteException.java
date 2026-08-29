package org.margin.server.storage.exceptions;

public class S3DeleteException extends RuntimeException {
    public S3DeleteException(Throwable cause) {
        super("Failed to delete file from S3", cause);
    }
}