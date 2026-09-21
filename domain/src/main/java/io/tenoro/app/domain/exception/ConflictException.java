package io.tenoro.app.domain.exception;

/**
 * Thrown when a request is well-formed and its referenced resources exist, but the current system
 * state prevents the operation (e.g. a duplicate Location code, an invalid task state transition).
 * Generic on purpose: reused by every aggregate in the replenishment module, not just Location.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
