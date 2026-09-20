package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.api.dto.rule.CreateReplenishmentRuleRequest;
import io.tenoro.app.api.dto.rule.ReplenishmentRuleResponse;
import io.tenoro.app.domain.model.ReplenishmentRule;
import io.tenoro.app.domain.port.inbound.ReplenishmentRuleService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.ReplenishmentRuleResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No local try/catch: exceptions propagate to the scoped ReplenishmentExceptionHandler
 * (infra/config/ReplenishmentExceptionHandler.java), per docs/ARCHITECTURE.md AD-05.
 *
 * Only POST /replenishment-rules: SPECS.md endpoint #5 defines no list/GET endpoint for this resource.
 * A later task (evaluate/generate replenishment tasks) reads rules internally through
 * ReplenishmentRuleRepository, not through HTTP.
 */
@RestController
@RequestMapping("/replenishment-rules")
@Tag(name = "Replenishment Rules", description = "Replenishment threshold management endpoints")
public class ReplenishmentRuleController {

    @Autowired
    private ReplenishmentRuleService replenishmentRuleService;

    @Operation(summary = "Define a replenishment rule",
            description = "Defines the min/max replenishment thresholds of a SKU at a picking location")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Replenishment rule created successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ReplenishmentRuleResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data (e.g. min > max, referenced location is not PICKING)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "The referenced location does not exist",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "A rule already exists for this SKU and location",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReplenishmentRuleResponse> createReplenishmentRule(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Replenishment rule creation details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateReplenishmentRuleRequest.class)
            )
        )
        @RequestBody CreateReplenishmentRuleRequest request
    ) {
        ReplenishmentRule rule = ReplenishmentRuleResponseMapper.toDomain(request);
        ReplenishmentRule created = replenishmentRuleService.create(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReplenishmentRuleResponseMapper.fromDomain(created));
    }
}
