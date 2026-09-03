package com.saas.platformtenant;

import java.util.Set;
import java.util.UUID;

/** Assinatura ativa de um tenant — o mesmo dado que cada serviço já buscava em
 *  SubscriptionResult, agora com um tipo comum para o {@link TenantSubscriptionResolver}. */
public record TenantSubscriptionInfo(
        UUID id,
        String status,
        String planCode,
        Set<String> moduleSlugSet
) {
}
