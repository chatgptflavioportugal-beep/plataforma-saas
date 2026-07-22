package com.saas.admin.dto;

import java.time.Instant;

/**
 * Payload de erro padrão do Admin Service — mesmo contrato usado pelo Usage Service.
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
}
