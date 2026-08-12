package com.gitinsight.authservice.config;

import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Security-specific error mapping for auth-service.
 * <p>
 * {@code @PreAuthorize} denials throw {@link AccessDeniedException} inside the
 * controller dispatch, where the shared {@code GlobalExceptionHandler}'s
 * RuntimeException handler (in the common module) would otherwise turn them
 * into 500s. This more-specific advice maps them to 403.
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(new ApiResponse<>(false, "Access denied", null),
                HttpStatus.FORBIDDEN);
    }
}
