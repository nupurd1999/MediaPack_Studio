package com.nupoor.mediapack;

import com.nupoor.mediapack.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    @Test
    @DisplayName("handleOptimisticLockingFailure — returns HTTP 409 CONFLICT with ProblemDetail")
    void testHandleOptimisticLockingFailure() {
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Version conflict");
        ResponseEntity<ProblemDetail> response = handler.handleOptimisticLockingFailure(ex, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Collaborative Edit Conflict", response.getBody().getTitle());
    }

    @Test
    @DisplayName("handleIllegalArgument — returns HTTP 404 for 'not found' message")
    void testHandleIllegalArgument_NotFound() {
        IllegalArgumentException ex = new IllegalArgumentException("Asset not found with ID: 123");
        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource Not Found", response.getBody().getTitle());
    }

    @Test
    @DisplayName("handleIllegalArgument — returns HTTP 400 for general invalid argument")
    void testHandleIllegalArgument_BadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Uploaded file cannot be null or empty");
        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid Argument", response.getBody().getTitle());
    }

    @Test
    @DisplayName("handleGenericException — returns HTTP 500 INTERNAL_SERVER_ERROR")
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected DB glitch");
        ResponseEntity<ProblemDetail> response = handler.handleGenericException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().getTitle());
    }
}
