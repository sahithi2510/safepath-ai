package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for errors encountered during communication with the ML service.
 */
public class MlServiceException extends SafePathException {

    public MlServiceException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public MlServiceException(String message, HttpStatus status) {
        super(message, status);
    }

    public MlServiceException(String message, Throwable cause, HttpStatus status) {
        super(message, status);
        initCause(cause);
    }
}
