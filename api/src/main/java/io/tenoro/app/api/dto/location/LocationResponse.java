package io.tenoro.app.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "LocationResponse",
    title = "Location Response",
    description = "Response object containing warehouse location information",
    requiredProperties = {"code", "type"}
)
public class LocationResponse {

    @Schema(
        name = "code",
        title = "Location Code",
        description = "Unique code identifying the location",
        example = "PICK-01"
    )
    private String code;

    @Schema(
        name = "type",
        title = "Location Type",
        description = "Type of the location",
        example = "PICKING",
        allowableValues = {"PICKING", "RESERVE"}
    )
    private String type;
}
