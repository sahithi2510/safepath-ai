package com.safepath.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all application-specific exceptions.
 */
@Getter
public class SafePathException extends RuntimeException {

    private final HttpStatus status;

    public SafePathException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
