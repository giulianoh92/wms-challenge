package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.api.dto.location.CreateLocationRequest;
import io.tenoro.app.api.dto.location.LocationResponse;
import io.tenoro.app.domain.model.Location;
import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.LocationResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No local try/catch: exceptions propagate to the scoped ReplenishmentExceptionHandler
 * (infra/config/ReplenishmentExceptionHandler.java), per docs/ARCHITECTURE.md AD-05.
 */
@RestController
@RequestMapping("/locations")
@Tag(name = "Locations", description = "Warehouse location management endpoints")
public class LocationController {

    @Autowired
    private LocationService locationService;

    @Operation(summary = "Create a new location", description = "Creates a new warehouse location with the given code and type")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Location created successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = LocationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data (e.g. unknown location type)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "A location with this code already exists",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Location creation details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateLocationRequest.class)
            )
        )
        @RequestBody CreateLocationRequest request
    ) {
        Location location = LocationResponseMapper.toDomain(request);
        Location created = locationService.create(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(LocationResponseMapper.fromDomain(created));
    }

    @Operation(summary = "Get all locations", description = "Retrieves every warehouse location")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Locations retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = LocationResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<LocationResponse>> getAllLocations() {
        List<LocationResponse> responses = locationService.findAll().stream()
                .map(LocationResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
