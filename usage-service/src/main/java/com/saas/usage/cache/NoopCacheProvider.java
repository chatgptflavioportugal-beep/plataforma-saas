package com.saas.usage.cache;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Implementação padrão enquanto o Redis não é ativado (ver contexto/fluxo-multi-tenant do
 * projeto): não guarda nada, delega toda a leitura/escrita ao Postgres via UsageCounterRepository.
 * Trocar por um RedisCacheProvider real é a única mudança necessária para ativar o cache.
 */
@ApplicationScoped
public class NoopCacheProvider implements UsageCacheProvider {

    @Override
    public Optional<Long> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, long value) {
        // sem-op — Postgres é a fonte de verdade
    }

    @Override
    public long incrementAndGet(String key, long delta) {
        return delta;
    }
}
