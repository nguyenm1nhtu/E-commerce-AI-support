package com.ecommerce.aicommercesupport.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ecommerce.aicommercesupport.common.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandle extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandle.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException exception, WebRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, WebRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access denied", Map.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException exception, WebRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Authentication required", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        log.error("Unhandled request exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        var message = reasonPhrase(status);

        if (status.is5xxServerError()) {
            log.error("Request processing failed", exception);
            message = "An unexpected error occurred";
        } else if (exception instanceof MethodArgumentNotValidException validation) {
            message = "Validation failed";
            validation.getBindingResult().getFieldErrors().forEach(error ->
                    errors.merge(error.getField(), error.getDefaultMessage() == null
                            ? "Invalid value" : error.getDefaultMessage(), (first, next) -> first + "; " + next));
            validation.getBindingResult().getGlobalErrors().forEach(error ->
                    errors.merge(error.getObjectName(), error.getDefaultMessage() == null
                            ? "Invalid value" : error.getDefaultMessage(), (first, next) -> first + "; " + next));
        } else if (exception instanceof HttpMessageNotReadableException) {
            message = "Malformed request body";
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Invalid value for parameter: " + mismatch.getName();
        } else if (exception instanceof ResponseStatusException responseStatus && responseStatus.getReason() != null) {
            message = responseStatus.getReason();
        }

        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, errorResponse(status, message, errors, request),
                responseHeaders, status, request);
    }

    private ResponseEntity<Object> response(HttpStatus status, String message,
            Map<String, String> errors, WebRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse(status, message, errors, request));
    }

    private ApiErrorResponse errorResponse(HttpStatusCode status, String message,
            Map<String, String> errors, WebRequest request) {
        var path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ApiErrorResponse(Instant.now(), status.value(), reasonPhrase(status), message, path, errors);
    }

    private String reasonPhrase(HttpStatusCode status) {
        var httpStatus = HttpStatus.resolve(status.value());
        return httpStatus == null ? "HTTP error" : httpStatus.getReasonPhrase();
    }
}
