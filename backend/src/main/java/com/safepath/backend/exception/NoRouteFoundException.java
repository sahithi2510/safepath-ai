package com.safepath.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the A* routing engine cannot discover a valid walkable path
 * between the requested start node and end node.
 */
public class NoRouteFoundException extends SafePathException {

    public NoRouteFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
