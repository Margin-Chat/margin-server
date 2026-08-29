package org.margin.server.shared.exceptions;

import com.mollie.mollie.models.errors.APIException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final int MAX_CAUSE_DEPTH = 20;

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatus());
        detail.setDetail(ex.getMessage());
        ex.getProperties().forEach(detail::setProperty);
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        detail.setDetail("File is too large. Maximum allowed size is 5MB.");
        return detail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatusCode());
        detail.setDetail(ex.getReason());
        return detail;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(APIException.class)
    public ProblemDetail handleAPIException(APIException ex) {
        log.error("Mollie API error", ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientDisconnect(Exception ex) {
        log.debug("Client disconnected before the response was written: {}", ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotWritableException.class, IOException.class})
    public void handleUnwritableResponse(Exception ex, HttpServletResponse response) throws IOException {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected before the response was written: {}", ex.getMessage());
            return;
        }
        log.error("Failed to write response", ex);
        if (!response.isCommitted()) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected before the response was written: {}", ex.getMessage());
        } else {
            log.error("Unhandled exception", ex);
        }
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("An unexpected error occurred");
        return detail;
    }

    private static boolean isClientDisconnect(Throwable ex) {
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof ClientAbortException || cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (cause instanceof IOException && isDisconnectMessage(cause.getMessage())) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("broken pipe") || lower.contains("connection reset");
    }
}