package com.example.shared.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String error,
        String message,
        int status,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, LocalDateTime.now());
    }

    public static ErrorResponse badRequest(String message) {
        return of("Bad Request", message, 400);
    }

    public static ErrorResponse notFound(String message) {
        return of("Not Found", message, 404);
    }

    public static ErrorResponse serviceUnavailable(String message) {
        return of("Service Unavailable", message, 503);
    }

    public static ErrorResponse internalError(String message) {
        return of("Internal Server Error", message, 500);
    }
}
