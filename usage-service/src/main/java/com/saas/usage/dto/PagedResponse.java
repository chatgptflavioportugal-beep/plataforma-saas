package com.saas.usage.dto;

import java.util.List;

/**
 * Envelope de paginação padrão do Usage Service — mesmo contrato usado pelo Admin Service.
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> items, int page, int pageSize, long totalItems) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;
        return new PagedResponse<>(items, page, pageSize, totalItems, totalPages);
    }
}
