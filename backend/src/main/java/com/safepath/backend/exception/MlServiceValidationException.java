package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the ML service rejects an inference request due to invalid input or batch limits.
 */
public class MlServiceValidationException extends MlServiceException {

    public MlServiceValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
