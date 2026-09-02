package com.saas.platformdatabase.query;

import com.saas.platformdatabase.exception.DatabaseQueryException;
import com.saas.platformdatabase.mapper.TupleTOMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uma unica execucao de native query, montada por {@link DatabaseQuery#nativeQuery}. Mantem
 * estado mutavel (os parametros acumulados por {@link #setParameter}) — por isso nao e
 * thread-safe nem reutilizavel entre consultas: cada chamada a {@code nativeQuery(...)} cria
 * uma instancia nova, e o uso esperado e montar e executar numa unica cadeia:
 *
 * <pre>
 * List&lt;UserTenantTO&gt; users = databaseQuery
 *         .nativeQuery(sql, UserTenantTO.class)
 *         .setParameter("tenantId", tenantId)
 *         .getResultList();
 * </pre>
 *
 * <p>Internamente executa via {@code EntityManager.createNativeQuery(sql, Tuple.class)} e
 * mapeia cada linha com {@code TupleTOMapper} — o DAO nunca precisa conhecer {@link Tuple}
 * nem {@code Object[]}.
 */
public final class NativeQuery<T> {

    private final EntityManager entityManager;
    private final String sql;
    private final Class<T> resultType;
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    NativeQuery(EntityManager entityManager, String sql, Class<T> resultType) {
        this.entityManager = entityManager;
        this.sql = sql;
        this.resultType = resultType;
    }

    /**
     * Passa {@code value} diretamente para {@link Query#setParameter(String, Object)} — o
     * Hibernate/JPA faz o binding preservando o tipo original ({@code UUID}, {@code Long},
     * {@code LocalDate}, {@code BigDecimal}, etc). Nunca converte o valor para {@code String}.
     */
    public NativeQuery<T> setParameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Executa a query e mapeia cada linha para {@code T}. O DAO nunca precisa de
     * {@code Tuple}, {@code Object[]}, {@code stream().map(...)} nem chamar o mapper.
     */
    public List<T> getResultList() {
        List<Tuple> tuples = executeQuery();
        List<T> results = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            results.add(TupleTOMapper.map(tuple, resultType));
        }
        return results;
    }

    /**
     * {@code Optional.empty()} para zero registros, {@code Optional.of(TO)} para exatamente
     * um. Mais de um registro nunca e descartado em silencio — indica que a query nao e tao
     * unica quanto o DAO esperava, entao lanca {@link DatabaseQueryException}.
     */
    public Optional<T> getOptionalResult() {
        List<T> results = getResultList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new DatabaseQueryException(
                    "Query esperava no maximo um resultado para " + resultType.getName()
                            + " mas retornou " + results.size() + " registros. SQL: " + sql);
        }
        return Optional.of(results.get(0));
    }

    /**
     * Alias de {@link #getOptionalResult()} — mesma semantica (0 -&gt; vazio, 1 -&gt;
     * presente, 2+ -&gt; {@link DatabaseQueryException}). Existe apenas para quem prefere a
     * nomenclatura mais proxima de {@code EntityManager#getSingleResult}; nao ha
     * comportamento adicional, entao nao duplica a logica.
     */
    public Optional<T> getSingleResult() {
        return getOptionalResult();
    }

    /**
     * Resultado bruto (alias de coluna -&gt; valor), sem popular um TO. Uso secundario — o
     * caminho principal da biblioteca e sempre native query -&gt; TO.
     */
    public List<Map<String, Object>> getRawResultList() {
        List<Tuple> tuples = executeQuery();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            rows.add(TupleTOMapper.toMap(tuple));
        }
        return rows;
    }

    /** Equivalente bruto de {@link #getOptionalResult()} — mesma regra para 2+ registros. */
    public Optional<Map<String, Object>> getRawSingleResult() {
        List<Map<String, Object>> rows = getRawResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new DatabaseQueryException(
                    "Query esperava no maximo um resultado (raw) mas retornou " + rows.size()
                            + " registros. SQL: " + sql);
        }
        return Optional.of(rows.get(0));
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> executeQuery() {
        try {
            Query query = entityManager.createNativeQuery(sql, Tuple.class);
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                query.setParameter(parameter.getKey(), parameter.getValue());
            }
            return query.getResultList();
        } catch (RuntimeException e) {
            throw new DatabaseQueryException(
                    "Falha ao executar native query para " + resultType.getName()
                            + " (parametros: " + parameters.keySet() + "). SQL: " + sql, e);
        }
    }
}
