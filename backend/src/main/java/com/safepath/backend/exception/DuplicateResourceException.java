package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends SafePathException {
    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
