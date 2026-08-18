package com.saas.profile.dto.tenant;

/**
 * Campos numéricos vêm de SQL nativo sem cast explícito (tipo JDBC real depende do driver);
 * mantidos como {@code Object} para reproduzir exatamente a serialização de hoje (Map bruto),
 * sem arriscar uma coerção de tipo incorreta.
 */
public record PlanDto(
        String id,
        String name,
        String code,
        String planType,
        Object priceMonthly,
        Object priceAnnual,
        Object maxUsers,
        Object maxAiRequestsMonth,
        String features,
        Object totalMonthlyPrice,
        Object totalAnnualMonthlyPrice,
        Object totalAnnualPrice
) {}
