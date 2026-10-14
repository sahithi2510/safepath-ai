package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for errors encountered during communication with the external weather service.
 */
public class WeatherClientException extends SafePathException {

    public WeatherClientException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public WeatherClientException(String message, HttpStatus status) {
        super(message, status);
    }

    public WeatherClientException(String message, Throwable cause, HttpStatus status) {
        super(message, status);
        initCause(cause);
    }
}
