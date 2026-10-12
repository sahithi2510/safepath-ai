package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends SafePathException {
    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
