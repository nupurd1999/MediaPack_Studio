package com.nupoor.mediapack.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler returning RFC 7807 ProblemDetail payloads.
 *
 * All API errors are standardized to the format:
 * {
 *   "type":     "https://mediapack.studio/errors/conflict",
 *   "title":    "Optimistic Locking Conflict",
 *   "status":   409,
 *   "detail":   "...",
 *   "instance": "/api/lightboxes/abc123/assets/sku-001",
 *   "timestamp": "...",
 *   "tenant":   "DE"
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles MongoDB @Version optimistic locking failures (HTTP 409 Conflict).
     * Triggered when two concurrent requests edit the same CollaborativeLightbox document.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, WebRequest request) {

        log.warn("Optimistic locking conflict detected: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://mediapack.studio/errors/optimistic-locking-conflict"));
        problem.setTitle("Collaborative Edit Conflict");
        problem.setDetail("The document was modified by another user after you loaded it. " +
                          "Please reload the latest version and retry your changes.");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("hint", "Reload the lightbox and re-apply your changes on top of the current version.");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * Handles resource-not-found cases from service layers (HTTP 404).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("Resource not found or invalid argument: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create("https://mediapack.studio/errors/resource-not-found"));
        problem.setTitle("Resource Not Found");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Fallback handler for all unexpected runtime exceptions (HTTP 500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://mediapack.studio/errors/internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please contact support if this persists.");
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
