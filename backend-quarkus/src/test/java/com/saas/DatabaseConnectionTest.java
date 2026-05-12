package com.saas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DatabaseConnectionTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void deveConectarAoBanco() {
        var result = em.createNativeQuery("SELECT 1").getSingleResult();
        assertEquals(1, ((Number) result).intValue(), "Conexão com o banco falhou");
    }

    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    void deveTerTabelaPlanos() {
        List<Object[]> planos = em.createNativeQuery(
            "SELECT code, name FROM plans ORDER BY sort_order"
        ).getResultList();

        assertFalse(planos.isEmpty(), "Tabela plans está vazia — rode o seed 0001_plans.sql");

        var codigos = planos.stream().map(r -> (String) r[0]).toList();
        assertTrue(codigos.contains("free"),    "Plano 'free' não encontrado");
        assertTrue(codigos.contains("starter"), "Plano 'starter' não encontrado");
        assertTrue(codigos.contains("pro"),     "Plano 'pro' não encontrado");

        System.out.println("Planos encontrados: " + codigos);
    }

    @Test
    @Transactional
    void deveTerTabelasTenants() {
        var tables = List.of("tenants", "user_tenants", "tenant_subscriptions",
                             "user_profiles", "audit_logs", "pdf_jobs");

        for (String table : tables) {
            var count = em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = '" + table + "'"
            ).getSingleResult();

            assertEquals(1L, ((Number) count).longValue(),
                "Tabela '" + table + "' não existe — verifique as migrations");
        }
    }
}
