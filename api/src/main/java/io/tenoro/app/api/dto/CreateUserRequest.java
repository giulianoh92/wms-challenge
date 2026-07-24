package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "CreateUserRequest",
    title = "Create User Request",
    description = "Request object for creating a new user",
    requiredProperties = {"name", "email"}
)
public class CreateUserRequest {

    @Schema(
        name = "name",
        title = "User Name",
        description = "Full name of the user",
        example = "John Doe",
        minLength = 1,
        maxLength = 100,
        type = "string"
    )
    private final String name;

    @Schema(
        name = "email",
        title = "Email Address",
        description = "User's email address",
        example = "john.doe@example.com",
        pattern = "^[A-Za-z0-9+_.-]+@(.+)$",
        type = "string"
    )
    private final String email;
}
