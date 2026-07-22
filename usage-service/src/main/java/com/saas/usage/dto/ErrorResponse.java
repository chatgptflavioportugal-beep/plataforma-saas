package com.saas.usage.dto;

import java.time.Instant;

/**
 * Payload de erro padrão do Usage Service — mesmo contrato usado pelo Admin Service.
 */
public record ErrorResponse(
        String code,
        String message,
        String details,
        Instant timestamp,
        String traceId
) {
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, null, Instant.now(), traceId);
    }

    public static ErrorResponse of(String code, String message, String details, String traceId) {
        return new ErrorResponse(code, message, details, Instant.now(), traceId);
    }
}
