package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.tenoro.app.api.dto.ErrorResponse;
import io.tenoro.app.api.dto.task.EvaluateReplenishmentRequest;
import io.tenoro.app.api.dto.task.EvaluateReplenishmentResponse;
import io.tenoro.app.api.dto.task.ReplenishmentTaskResponse;
import io.tenoro.app.domain.model.ReplenishmentEvaluationResult;
import io.tenoro.app.domain.port.inbound.ReplenishmentTaskService;
import io.tenoro.app.infra.adapter.inbound.web.mappers.ReplenishmentTaskResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * Only POST /replenishment/tasks (evaluate/generate, FR-TSK-01) and GET /replenishment/tasks (list,
 * FR-TSK-02) in this task. /confirm and /cancel (FR-TSK-03/04) are a separate later task — this controller
 * is deliberately left open for them, not sealed.
 */
@RestController
@RequestMapping("/replenishment/tasks")
@Tag(name = "Replenishment Tasks", description = "Replenishment task evaluation/generation and lifecycle endpoints")
public class ReplenishmentTaskController {

    @Autowired
    private ReplenishmentTaskService replenishmentTaskService;

    @Operation(summary = "Evaluate and generate replenishment tasks",
            description = "Evaluates a SKU's stock at a picking location against its replenishment rule and generates 0..n OPEN replenishment tasks from reserve locations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Evaluation completed successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = EvaluateReplenishmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data (e.g. referenced location is not PICKING)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "The referenced location, or its replenishment rule, does not exist",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<EvaluateReplenishmentResponse> evaluateReplenishment(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "SKU and picking location to evaluate",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = EvaluateReplenishmentRequest.class)
            )
        )
        @RequestBody EvaluateReplenishmentRequest request
    ) {
        ReplenishmentEvaluationResult result =
                replenishmentTaskService.evaluate(request.getSku(), request.getLocationCode());
        return ResponseEntity.ok(ReplenishmentTaskResponseMapper.fromDomain(result));
    }

    @Operation(summary = "Get all replenishment tasks", description = "Retrieves every replenishment task, regardless of status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Replenishment tasks retrieved successfully",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ReplenishmentTaskResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ReplenishmentTaskResponse>> getAllReplenishmentTasks() {
        List<ReplenishmentTaskResponse> responses = replenishmentTaskService.findAll().stream()
                .map(ReplenishmentTaskResponseMapper::fromDomain)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
