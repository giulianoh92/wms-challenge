package io.tenoro.app.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "CreateLocationRequest",
    title = "Create Location Request",
    description = "Request object for creating a new warehouse location",
    requiredProperties = {"code", "type"}
)
public class CreateLocationRequest {

    @Schema(
        name = "code",
        title = "Location Code",
        description = "Unique code identifying the location",
        example = "PICK-01",
        minLength = 1,
        type = "string"
    )
    private final String code;

    @Schema(
        name = "type",
        title = "Location Type",
        description = "Type of the location",
        example = "PICKING",
        allowableValues = {"PICKING", "RESERVE"},
        type = "string"
    )
    private final String type;
}
