package com.example.backend.exception;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(message);
    }
}

