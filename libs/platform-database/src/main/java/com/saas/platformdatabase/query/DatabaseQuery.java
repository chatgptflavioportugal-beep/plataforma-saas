package com.saas.platformdatabase.query;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Ponto de entrada da API fluente de native query da plataforma. Bean CDI
 * ({@code @ApplicationScoped}) sem estado proprio — injete com {@code @Inject} como
 * qualquer outro bean da plataforma, sem precisar instanciar manualmente:
 *
 * <pre>
 * &#64;ApplicationScoped
 * public class UserTenantDAO {
 *
 *     &#64;Inject
 *     EntityManager em;
 *
 *     &#64;Inject
 *     DatabaseQuery databaseQuery;
 *
 *     public Optional&lt;UserTenantTO&gt; findByUserAndTenant(UUID userId, UUID tenantId) {
 *         return databaseQuery
 *                 .nativeQuery(em, sql, UserTenantTO.class)
 *                 .setParameter("userId", userId)
 *                 .getOptionalResult();
 *     }
 * }
 * </pre>
 *
 * <p>O {@link EntityManager} e informado a cada chamada de {@link #nativeQuery}, nao no
 * momento da injecao — este bean nunca guarda uma referencia a ele, nunca cria conexao ou
 * {@code EntityManager} proprios, e nunca gerencia transacao (isso continua sendo
 * responsabilidade do DAO/Negocio via {@code @Transactional}, como hoje). Por nao ter
 * estado, a mesma instancia (singleton de aplicacao) e compartilhada e reaproveitada por
 * todos os DAOs do servico sem qualquer risco de concorrencia.
 */
@ApplicationScoped
public class DatabaseQuery {

    /**
     * Inicia uma nova native query sobre o {@code entityManager} informado pelo chamador.
     * Cada chamada cria uma {@link NativeQuery} nova — a instancia retornada acumula os
     * parametros desta consulta especifica e nao deve ser compartilhada entre
     * threads/requisicoes concorrentes.
     *
     * @param entityManager EntityManager do servico consumidor (o mesmo injetado no DAO
     *                      chamador via {@code @Inject}).
     * @param sql           SQL nativo (Oracle ou PostgreSQL); colunas sem alias explicito
     *                      usam o proprio nome da coluna como alias (suficiente quando ele
     *                      ja bate com o {@code @Column} do TO).
     * @param resultType    TO esperado, com campos anotados com {@code @Column}.
     */
    public <T> NativeQuery<T> nativeQuery(EntityManager entityManager, String sql, Class<T> resultType) {
        return new NativeQuery<>(entityManager, sql, resultType);
    }
}
