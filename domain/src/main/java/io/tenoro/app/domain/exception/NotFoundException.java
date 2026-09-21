package io.tenoro.app.domain.exception;

/**
 * Thrown when a referenced resource (e.g. a Location code, a ReplenishmentTask id) does not exist.
 * Generic on purpose: reused by every aggregate in the replenishment module, not just Location.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
