package com.desktopcat.server.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> ERROR_CODES = Map.ofEntries(
            Map.entry("Request body is required.", "REQUEST_BODY_REQUIRED"),
            Map.entry("Profile id is required.", "PROFILE_ID_REQUIRED"),
            Map.entry("Profile id must be positive.", "PROFILE_ID_INVALID"),
            Map.entry("Cat name is required.", "CAT_NAME_REQUIRED"),
            Map.entry("Cat name must not exceed 20 characters.", "CAT_NAME_TOO_LONG"),
            Map.entry("Profile version is required.", "PROFILE_VERSION_REQUIRED"),
            Map.entry("Profile version must not be negative.", "PROFILE_VERSION_INVALID"),
            Map.entry("Cat profile was not found.", "CAT_PROFILE_NOT_FOUND"),
            Map.entry("Cat profile has been updated. Refresh and try again.",
                    "CAT_PROFILE_VERSION_CONFLICT"));

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        String code = ERROR_CODES.getOrDefault(message, "REQUEST_FAILED");
        return build(status, code, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "REQUEST_BODY_INVALID", "Request body is invalid.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Request method is not allowed.", request);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleDisconnectedStream(HttpServletRequest request) {
        log.debug("event=stream_client_disconnected method={} path={}",
                request.getMethod(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("event=unhandled_request_error method={} path={}",
                request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        Object requestIdAttribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        String requestId = requestIdAttribute instanceof String value ? value : "unavailable";
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code, message, requestId, Instant.now(), request.getRequestURI(), List.of()));
    }
}
