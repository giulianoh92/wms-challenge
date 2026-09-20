package io.tenoro.app.infra.config;

import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.infra.adapter.inbound.web.LocationController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Scoped exception handler for the replenishment module controllers (docs/ARCHITECTURE.md AD-05).
 *
 * Intentionally NOT a global handler: assignableTypes limits it to exactly the controller class(es)
 * listed below, not to their package (basePackageClasses would scope by package instead, which would
 * also cover UserController/HelloController since they live in the same package). Add every new
 * replenishment-module controller class to assignableTypes as later tasks introduce them
 * (StockController, ReplenishmentRuleController, ReplenishmentTaskController).
 *
 * This never intercepts UserController's exceptions because UserController already catches everything
 * internally in its own try/catch/finally and never lets an exception propagate (see CLAUDE.md) — but
 * assignableTypes makes that a belt-and-braces guarantee rather than an accident of package layout.
 */
@RestControllerAdvice(assignableTypes = {LocationController.class})
public class ReplenishmentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value(), Instant.now().toString()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value(), Instant.now().toString()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), Instant.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value(), Instant.now().toString()));
    }
}
