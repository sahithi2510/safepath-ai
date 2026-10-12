package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the ML service is unreachable, timed out, or unready (models not loaded).
 */
public class MlServiceUnavailableException extends MlServiceException {

    public MlServiceUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public MlServiceUnavailableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
