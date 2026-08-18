package com.saas.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O frontend consome esta chave como {@code updatedAt} (camelCase) — sem o
 * @JsonProperty aqui, a estratégia global SNAKE_CASE do Jackson produziria
 * {@code updated_at} e quebraria o contrato existente.
 */
public record PlatformSettingDTO(
        String key,
        String value,
        String description,
        @JsonProperty("updatedAt") String updatedAt) {
}
