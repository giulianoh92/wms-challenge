package io.tenoro.app.infra.adapter.inbound.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Hello", description = "Simple greeting endpoints for testing and health check")
public class HelloController {

    @Operation(
        summary = "Get basic hello message",
        description = "Returns a simple hello world message. Useful for basic connectivity testing."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully returns greeting message",
        content = @Content(
            mediaType = "text/plain",
            schema = @Schema(
                type = "string",
                example = "Hello, World!",
                description = "A simple greeting message"
            )
        )
    )
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }

    @Operation(
        summary = "Get personalized greeting",
        description = "Returns a personalized greeting message using the provided name"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully returns personalized greeting",
            content = @Content(
                mediaType = "text/plain",
                schema = @Schema(
                    type = "string",
                    example = "Hello, John!",
                    description = "A personalized greeting message"
                )
            )
        )
    })
    @GetMapping("/hello/greeting")
    public String greeting(
        @Parameter(
            description = "Name of the person to greet",
            example = "John",
            schema = @Schema(
                type = "string",
                minLength = 1,
                maxLength = 100
            )
        )
        @RequestParam(value = "name", defaultValue = "World") String name
    ) {
        return String.format("Hello, %s!", name);
    }
}
