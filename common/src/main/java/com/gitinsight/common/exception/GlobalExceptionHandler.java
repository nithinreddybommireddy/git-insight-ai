package com.gitinsight.common.exception;

import com.gitinsight.common.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Message returned to clients for unexpected 500s. Full exception details go
     * to the server log only — never expose internal messages (DB errors, GitHub
     * internals, PDF parsing details, URLs) to callers.
     */
    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : fe.getField() + " is invalid")
                .orElse("Validation failed");
        ApiResponse<Void> response = new ApiResponse<>(false, message, null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ApiResponse<Void> response = new ApiResponse<>(false, "Malformed request body", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();

        // Safe, deliberate client-facing statuses only. The "not found" and
        // "rate limit" strings are the service-level contract (frontend and
        // integration tests depend on them) and contain no internals.
        if (message != null && message.contains("not found")) {
            return new ResponseEntity<>(new ApiResponse<>(false, message, null), HttpStatus.NOT_FOUND);
        }
        if (message != null && message.contains("rate limit")) {
            return new ResponseEntity<>(new ApiResponse<>(false, message, null), HttpStatus.TOO_MANY_REQUESTS);
        }

        // Everything else: log the full stack trace, return a generic message.
        log.error("Unhandled runtime exception", ex);
        return new ResponseEntity<>(
                new ApiResponse<>(false, GENERIC_ERROR_MESSAGE, null),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(
                new ApiResponse<>(false, GENERIC_ERROR_MESSAGE, null),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
