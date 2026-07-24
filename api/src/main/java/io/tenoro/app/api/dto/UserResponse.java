package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(
    name = "UserResponse",
    title = "User Response",
    description = "Response object containing user information",
    requiredProperties = {"id", "name", "email", "createdAt", "updatedAt"}
)
public class UserResponse {
    @Schema(
        name = "id",
        title = "User ID",
        description = "Unique identifier for the user",
        example = "123e4567-e89b-12d3-a456-426614174000",
        pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        minLength = 36,
        maxLength = 36
    )
    private String id;

    @Schema(
        name = "name",
        title = "User Name",
        description = "Full name of the user",
        example = "John Doe"
    )
    private String name;

    @Schema(
        name = "email",
        title = "User Email",
        description = "Email address of the user",
        example = "john.doe@example.com"
    )
    private String email;

    @Schema(
        name = "createdAt",
        title = "Creation Date",
        description = "Date and time when the user was created"
    )
    private LocalDateTime createdAt;

    @Schema(
        name = "updatedAt",
        title = "Last Update Date",
        description = "Date and time when the user was last updated"
    )
    private LocalDateTime updatedAt;
}
