package org.margin.server.storage.exceptions;

public class S3UploadException extends RuntimeException {
    public S3UploadException(String label, Throwable cause) {
        super("Failed to upload " + label + " to S3", cause);
    }
}