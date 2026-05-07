package org.margin.server.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.calls.exceptions.CallValidationException;
import org.margin.server.social.channel.exceptions.ChannelNotFoundException;
import org.margin.server.social.margin.exceptions.MarginNotFoundException;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MarginNotFoundException.class)
    public ProblemDetail handleMarginNotFound(MarginNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(ChannelNotFoundException.class)
    public ProblemDetail handleChannelNotFound(ChannelNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        detail.setDetail("File is too large. Maximum allowed size is 5MB.");
        return detail;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatusCode());
        detail.setDetail(ex.getReason());
        return detail;
    }

    @ExceptionHandler(CallValidationException.class)
    public ProblemDetail handleCallValidation(CallValidationException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail handleDuplicateKey(DuplicateKeyException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(SubscriptionLimitExceededException.class)
    public ProblemDetail handleSubscriptionLimit(SubscriptionLimitExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.PAYMENT_REQUIRED);
        detail.setDetail(ex.getMessage());
        detail.setProperty("tier", ex.getTier());
        detail.setProperty("limit", ex.getLimit());
        return detail;
    }
}