package io.tenoro.app.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response containing error details")
public class ErrorResponse {

    @Schema(description = "Error message describing what went wrong", example = "Invalid input data")
    private String message;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Timestamp of the error", example = "2023-09-12T10:30:00Z")
    private String timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(String message, int status, String timestamp) {
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
