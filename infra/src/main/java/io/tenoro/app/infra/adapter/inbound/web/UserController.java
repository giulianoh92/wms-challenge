package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.CreateUserRequest;
import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.api.dto.UpdateUserRequest;
import io.tenoro.app.api.dto.UserResponse;
import io.tenoro.app.domain.model.User;
import io.tenoro.app.domain.model.UserId;
import io.tenoro.app.domain.port.inbound.UserService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.UserResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Operation(summary = "Create a new user", description = "Creates a new user with the provided name and email")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<?> createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User creation details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateUserRequest.class)
            )
        )
        @RequestBody CreateUserRequest request
    ) {
        long startTime = System.currentTimeMillis();
        logger.info("POST /users - Creating new user with name: '{}', email: '{}'",
                   request.getName(), request.getEmail());

        try {
            User user = userService.createUser(request.getName(), request.getEmail());
            UserResponse response = UserResponseMapper.fromDomain(user);

            logDuration("POST /users", user.getId().getValue().toString(), startTime);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logErrorDuration("POST /users", request.getEmail(), e.getMessage(), startTime);
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), 400, Instant.now().toString());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logUnexpectedErrorDuration("POST /users", request.getEmail(), e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid user ID format",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
        @Parameter(
            description = "User's unique identifier",
            required = true,
            schema = @Schema(type = "string", format = "uuid")
        )
        @PathVariable("id") String id
    ) {
        long startTime = System.currentTimeMillis();
        // Add structured logging with MDC for better trace correlation
        MDC.put("userId", id);
        MDC.put("endpoint", "GET /users/{id}");
        MDC.put("timestamp", Instant.now().toString());

        logger.info("GET /users/{} - Retrieving user by ID", id);

        try {
            UserId userId = UserId.of(id);
            Optional<User> user = userService.getUserById(userId);

            if (user.isPresent()) {
                logDuration("GET /users/{}", id, startTime);
                return ResponseEntity.ok(UserResponseMapper.fromDomain(user.get()));
            } else {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("GET /users/{} - User not found, duration: {}ms", id, duration);
                ErrorResponse errorResponse = new ErrorResponse("User not found", 404, Instant.now().toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
        } catch (IllegalArgumentException e) {
            logErrorDuration("GET /users/{}", id, e.getMessage(), startTime);
            ErrorResponse errorResponse = new ErrorResponse("Invalid user ID format: " + id, 400, Instant.now().toString());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logUnexpectedErrorDuration("GET /users/{}", id, e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Get all users", description = "Retrieves a list of all users in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        long startTime = System.currentTimeMillis();
        logger.info("GET /users - Retrieving all users");

        try {
            List<User> users = userService.getAllUsers();
            List<UserResponse> responses = users.stream()
                    .map(UserResponseMapper::fromDomain)
                    .toList();

            logDuration("GET /users", "all users", startTime);

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logUnexpectedErrorDuration("GET /users", "all users", e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Get user by email", description = "Retrieves a user by their email address")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/by-email")
    public ResponseEntity<?> getUserByEmail(
        @Parameter(description = "User's email address", required = true)
        @RequestParam("email") String email
    ) {
        long startTime = System.currentTimeMillis();
        logger.info("GET /users/by-email - Searching user by email: '{}'", email);

        try {
            Optional<User> user = userService.getUserByEmail(email);

            if (user.isPresent()) {
                logDuration("GET /users/by-email", email, startTime);
                return ResponseEntity.ok(UserResponseMapper.fromDomain(user.get()));
            } else {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("GET /users/by-email - No user found with email: '{}', duration: {}ms",
                           email, duration);
                ErrorResponse errorResponse = new ErrorResponse("User not found with email: " + email, 404, Instant.now().toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
        } catch (Exception e) {
            logUnexpectedErrorDuration("GET /users/by-email", email, e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Update user name", description = "Updates the name of an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User name updated successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}/name")
    public ResponseEntity<?> updateUserName(
        @Parameter(description = "User's unique identifier", required = true)
        @PathVariable("id") String id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User name update details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateUserRequest.class)
            )
        )
        @RequestBody UpdateUserRequest request
    ) {
        long startTime = System.currentTimeMillis();
        logger.info("PUT /users/{}/name - Updating user name to: '{}'", id, request.getName());

        try {
            UserId userId = UserId.of(id);
            User updatedUser = userService.updateUserName(userId, request.getName());
            UserResponse response = UserResponseMapper.fromDomain(updatedUser);

            logDuration("PUT /users/{}/name", id, startTime);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logErrorDuration("PUT /users/{}/name", id, e.getMessage(), startTime);
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), 400, Instant.now().toString());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (RuntimeException e) {
            logErrorDuration("PUT /users/{}/name", id, "User not found", startTime);
            ErrorResponse errorResponse = new ErrorResponse("User not found", 404, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logUnexpectedErrorDuration("PUT /users/{}/name", id, e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Update user email", description = "Updates the email of an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User email updated successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}/email")
    public ResponseEntity<?> updateUserEmail(
        @Parameter(description = "User's unique identifier", required = true)
        @PathVariable("id") String id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User email update details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateUserRequest.class)
            )
        )
        @RequestBody UpdateUserRequest request
    ) {
        long startTime = System.currentTimeMillis();
        logger.info("PUT /users/{}/email - Updating user email to: '{}'", id, request.getEmail());

        try {
            UserId userId = UserId.of(id);
            User updatedUser = userService.updateUserEmail(userId, request.getEmail());
            UserResponse response = UserResponseMapper.fromDomain(updatedUser);

            logDuration("PUT /users/{}/email", id, startTime);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logErrorDuration("PUT /users/{}/email", id, e.getMessage(), startTime);
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), 400, Instant.now().toString());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (RuntimeException e) {
            logErrorDuration("PUT /users/{}/email", id, "User not found", startTime);
            ErrorResponse errorResponse = new ErrorResponse("User not found", 404, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            logUnexpectedErrorDuration("PUT /users/{}/email", id, e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @Operation(summary = "Delete user", description = "Deletes an existing user by their unique ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid user ID format",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
        @Parameter(description = "User's unique identifier", required = true)
        @PathVariable("id") String id
    ) {
        long startTime = System.currentTimeMillis();
        logger.info("DELETE /users/{} - Attempting to delete user", id);

        try {
            UserId userId = UserId.of(id);
            boolean deleted = userService.deleteUser(userId);

            if (deleted) {
                logDuration("DELETE /users/{}", id, startTime);
                return ResponseEntity.noContent().build();
            } else {
                long duration = System.currentTimeMillis() - startTime;
                logger.warn("DELETE /users/{} - User not found for deletion, duration: {}ms", id, duration);
                ErrorResponse errorResponse = new ErrorResponse("User not found", 404, Instant.now().toString());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
        } catch (IllegalArgumentException e) {
            logErrorDuration("DELETE /users/{}", id, e.getMessage(), startTime);
            ErrorResponse errorResponse = new ErrorResponse("Invalid user ID format: " + id, 400, Instant.now().toString());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logUnexpectedErrorDuration("DELETE /users/{}", id, e.getMessage(), startTime, e);
            ErrorResponse errorResponse = new ErrorResponse("Internal server error", 500, Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Helper method to log duration and create response
     */
    private void logDuration(String operation, String id, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        logger.info("{} - Operation completed successfully for ID: '{}', duration: {}ms", operation, id, duration);
    }

    /**
     * Helper method to log duration for errors
     */
    private void logErrorDuration(String operation, String id, String message, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        logger.warn("{} - Error for ID: '{}': {}, duration: {}ms", operation, id, message, duration);
    }

    /**
     * Helper method to log duration for unexpected errors
     */
    private void logUnexpectedErrorDuration(String operation, String id, String message, long startTime, Exception e) {
        long duration = System.currentTimeMillis() - startTime;
        logger.error("{} - Unexpected error for ID: '{}': {}, duration: {}ms", operation, id, message, duration, e);
    }
}
