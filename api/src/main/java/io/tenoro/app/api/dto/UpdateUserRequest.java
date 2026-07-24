package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "UpdateUserRequest",
    title = "Update User Request",
    description = "Request object for updating user information"
)
public class UpdateUserRequest {

    @Schema(
        name = "name",
        title = "User Name",
        description = "New name for the user",
        example = "Jane Doe",
        minLength = 1,
        maxLength = 100,
        type = "string"
    )
    private final String name;

    @Schema(
        name = "email",
        title = "Email Address",
        description = "New email address for the user",
        example = "jane.doe@example.com",
        pattern = "^[A-Za-z0-9+_.-]+@(.+)$",
        type = "string"
    )
    private final String email;

}
