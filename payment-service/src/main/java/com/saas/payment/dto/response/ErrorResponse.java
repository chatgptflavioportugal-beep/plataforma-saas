package com.saas.payment.dto.response;

import java.time.Instant;

/**
 * Payload de erro padrão do Payment Service — mesmo contrato usado pelos
 * demais microsserviços Quarkus da plataforma.
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
