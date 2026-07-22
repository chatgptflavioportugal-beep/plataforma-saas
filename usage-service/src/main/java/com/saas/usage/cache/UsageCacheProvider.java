package com.saas.usage.cache;

import java.util.Optional;

/**
 * Abstração de cache para contadores de uso. Hoje o Postgres é a fonte de verdade
 * (ver UsageCounterRepository); esta interface é o ponto de extensão para introduzir
 * Redis no futuro sem alterar UsageService. Não ativar nenhuma implementação real ainda.
 */
public interface UsageCacheProvider {

    Optional<Long> get(String key);

    void put(String key, long value);

    long incrementAndGet(String key, long delta);
}
