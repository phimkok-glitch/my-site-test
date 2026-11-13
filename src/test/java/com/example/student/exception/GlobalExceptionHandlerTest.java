package com.example.student.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void entityNotFound() {
        ResponseEntity<AppError> response =
                handler.catchEntityNotFoundException(new EntityNotFoundException("not found"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("not found", response.getBody().getMessage());
    }

    @Test
    void emailAlreadyExists() {
        ResponseEntity<AppError> response =
                handler.catchEmailAlreadyExistsException(new EmailAlreadyExistsException("exists"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("exists", response.getBody().getMessage());
    }
}
