package com.saas.platformtenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta a assinatura ativa de um tenant. Cada serviço implementa isto sobre seu próprio DAO
 * (SQL nativo ou Panache) — a lib não assume nenhum ORM/estilo de persistência específico.
 */
public interface TenantSubscriptionResolver {

    Optional<TenantSubscriptionInfo> findActiveByTenant(UUID tenantId);
}
