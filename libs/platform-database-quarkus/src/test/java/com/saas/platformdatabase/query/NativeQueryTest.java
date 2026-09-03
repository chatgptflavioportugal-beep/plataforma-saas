package com.saas.platformdatabase.query;

import com.saas.platformdatabase.exception.DatabaseQueryException;
import com.saas.platformdatabase.support.FakeTuple;
import com.saas.platformdatabase.support.SampleUserTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeQueryTest {

    // DatabaseQuery e um bean CDI sem estado — nos testes, instanciado direto com "new"
    // (igual a qualquer POJO), sem precisar subir container CDI algum.
    private final DatabaseQuery databaseQuery = new DatabaseQuery();

    private EntityManager entityManager;
    private Query jpaQuery;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        jpaQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(jpaQuery);
        when(jpaQuery.setParameter(anyString(), any())).thenReturn(jpaQuery);
    }

    @Test
    void setParameterPassesValueDirectlyToJpaQueryWithoutConvertingToString() {
        UUID userId = UUID.randomUUID();
        when(jpaQuery.getResultList()).thenReturn(List.of());

        databaseQuery
                .nativeQuery(entityManager, "SELECT 1", SampleUserTO.class)
                .setParameter("userId", userId)
                .getResultList();

        // O mesmo objeto UUID, nao userId.toString() — o binding de tipo fica com o Hibernate/JPA.
        verify(jpaQuery).setParameter("userId", userId);
    }

    @Test
    void getResultListMapsEveryTupleToTO_noTupleOrObjectArrayLeaksToCaller() {
        FakeTuple tuple1 = new FakeTuple().with("email", "a@example.com");
        FakeTuple tuple2 = new FakeTuple().with("email", "b@example.com");
        when(jpaQuery.getResultList()).thenReturn(List.of(tuple1, tuple2));

        List<SampleUserTO> results = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class)
                .getResultList();

        assertEquals(2, results.size());
        assertEquals("a@example.com", results.get(0).getEmail());
        assertEquals("b@example.com", results.get(1).getEmail());
    }

    @Test
    void getOptionalResultReturnsEmptyForNoRows() {
        when(jpaQuery.getResultList()).thenReturn(List.of());

        Optional<SampleUserTO> result = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class)
                .getOptionalResult();

        assertTrue(result.isEmpty());
    }

    @Test
    void getOptionalResultReturnsThePresentTOForExactlyOneRow() {
        FakeTuple tuple = new FakeTuple().with("email", "only@example.com");
        when(jpaQuery.getResultList()).thenReturn(List.of(tuple));

        Optional<SampleUserTO> result = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class)
                .getOptionalResult();

        assertTrue(result.isPresent());
        assertEquals("only@example.com", result.get().getEmail());
    }

    @Test
    void getOptionalResultThrowsWhenQueryReturnsMoreThanOneRow_neverSilentlyDropsExtraRows() {
        FakeTuple tuple1 = new FakeTuple().with("email", "a@example.com");
        FakeTuple tuple2 = new FakeTuple().with("email", "b@example.com");
        when(jpaQuery.getResultList()).thenReturn(List.of(tuple1, tuple2));

        NativeQuery<SampleUserTO> query = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class);

        DatabaseQueryException exception = assertThrows(DatabaseQueryException.class, query::getOptionalResult);
        assertTrue(exception.getMessage().contains("2"));
    }

    @Test
    void getSingleResultBehavesExactlyLikeGetOptionalResult() {
        FakeTuple tuple = new FakeTuple().with("email", "only@example.com");
        when(jpaQuery.getResultList()).thenReturn(List.of(tuple));

        Optional<SampleUserTO> result = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class)
                .getSingleResult();

        assertTrue(result.isPresent());
        assertEquals("only@example.com", result.get().getEmail());
    }

    @Test
    void getRawResultListReturnsColumnMapsWithoutPopulatingATO() {
        FakeTuple tuple = new FakeTuple().with("email", "raw@example.com");
        when(jpaQuery.getResultList()).thenReturn(List.of(tuple));

        List<Map<String, Object>> rows = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class)
                .getRawResultList();

        assertEquals(1, rows.size());
        assertEquals("raw@example.com", rows.get(0).get("email"));
    }

    @Test
    void multipleSetParameterCallsAreAllAppliedBeforeExecution() {
        when(jpaQuery.getResultList()).thenReturn(List.of());
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        databaseQuery
                .nativeQuery(entityManager, "SELECT 1", SampleUserTO.class)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getResultList();

        verify(jpaQuery).setParameter("userId", userId);
        verify(jpaQuery).setParameter("tenantId", tenantId);
        verify(jpaQuery, times(2)).setParameter(anyString(), any());
    }

    @Test
    void queryExecutionFailureIsWrappedInDatabaseQueryException_neverSwallowed() {
        when(jpaQuery.getResultList()).thenThrow(new RuntimeException("boom"));

        NativeQuery<SampleUserTO> query = databaseQuery
                .nativeQuery(entityManager, "SELECT email FROM users", SampleUserTO.class);

        DatabaseQueryException exception = assertThrows(DatabaseQueryException.class, query::getResultList);
        assertEquals("boom", exception.getCause().getMessage());
    }

    @Test
    void eachNativeQueryCallCreatesAFreshIndependentInstance() {
        when(jpaQuery.getResultList()).thenReturn(List.of());

        databaseQuery.nativeQuery(entityManager, "SELECT 1", SampleUserTO.class)
                .setParameter("userId", UUID.randomUUID());
        databaseQuery.nativeQuery(entityManager, "SELECT 1", SampleUserTO.class).getResultList();

        // A segunda instancia nao herda o parametro setado na primeira.
        verify(jpaQuery, never()).setParameter(eq("userId"), any());
    }
}
