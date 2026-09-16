package com.desktopcat.server.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
                    "CAT_PROFILE_VERSION_CONFLICT"),
            Map.entry("Record id is required.", "RECORD_ID_REQUIRED"),
            Map.entry("Record id must be positive.", "RECORD_ID_INVALID"),
            Map.entry("Record type is required.", "RECORD_TYPE_REQUIRED"),
            Map.entry("Record type must be DIARY, THOUGHT, or WORK_NOTE.", "RECORD_TYPE_INVALID"),
            Map.entry("Record title must not exceed 120 characters.", "RECORD_TITLE_TOO_LONG"),
            Map.entry("Record content is required.", "RECORD_CONTENT_REQUIRED"),
            Map.entry("Record content must not exceed 100000 characters.", "RECORD_CONTENT_TOO_LONG"),
            Map.entry("Record date is required.", "RECORD_DATE_REQUIRED"),
            Map.entry("Record mood must not exceed 32 characters.", "RECORD_MOOD_TOO_LONG"),
            Map.entry("Record version is required.", "RECORD_VERSION_REQUIRED"),
            Map.entry("Record version must not be negative.", "RECORD_VERSION_INVALID"),
            Map.entry("Record id must be a number.", "RECORD_ID_INVALID"),
            Map.entry("Record version must be a number.", "RECORD_VERSION_INVALID"),
            Map.entry("Page must be at least 1.", "PAGE_INVALID"),
            Map.entry("Page must be a number.", "PAGE_INVALID"),
            Map.entry("Page size must be between 1 and 100.", "PAGE_SIZE_INVALID"),
            Map.entry("Page size must be a number.", "PAGE_SIZE_INVALID"),
            Map.entry("Requested page is too large.", "PAGE_TOO_LARGE"),
            Map.entry("Recall limit must be between 1 and 50.", "RECALL_LIMIT_INVALID"),
            Map.entry("Recall limit must be a number.", "RECALL_LIMIT_INVALID"),
            Map.entry("Personal record was not found.", "PERSONAL_RECORD_NOT_FOUND"),
            Map.entry("Personal record has been updated. Refresh and try again.",
                    "PERSONAL_RECORD_VERSION_CONFLICT"),
            Map.entry("Personal record could not be created.", "PERSONAL_RECORD_CREATE_FAILED"));

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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String message = switch (exception.getName()) {
            case "recordId" -> "Record id must be a number.";
            case "version" -> "Record version must be a number.";
            case "page" -> "Page must be a number.";
            case "pageSize" -> "Page size must be a number.";
            case "limit" -> "Recall limit must be a number.";
            default -> "Request parameter is invalid.";
        };
        String code = ERROR_CODES.getOrDefault(message, "REQUEST_PARAMETER_INVALID");
        return build(HttpStatus.BAD_REQUEST, code, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Request method is not allowed.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_TYPE_NOT_SUPPORTED",
                "Request media type is not supported.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "Requested resource was not found.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(
            DataAccessException exception, HttpServletRequest request) {
        log.error("event=data_access_error method={} path={} exceptionType={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_OPERATION_FAILED",
                "A database operation failed.", request);
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
