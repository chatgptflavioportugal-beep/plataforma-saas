package com.saas.platformdatabase.query;

import com.saas.platformdatabase.support.SampleUserTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;

class DatabaseQueryTest {

    @Test
    void nativeQueryReturnsANewIndependentInstanceEveryCall() {
        DatabaseQuery databaseQuery = new DatabaseQuery();
        EntityManager entityManager = mock(EntityManager.class);

        NativeQuery<SampleUserTO> first = databaseQuery.nativeQuery(entityManager, "SELECT 1", SampleUserTO.class);
        NativeQuery<SampleUserTO> second = databaseQuery.nativeQuery(entityManager, "SELECT 1", SampleUserTO.class);

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    void statelessBeanCanBeReusedAcrossDifferentEntityManagersAndQueries() {
        DatabaseQuery databaseQuery = new DatabaseQuery();

        assertNotNull(databaseQuery.nativeQuery(mock(EntityManager.class), "SELECT 1", SampleUserTO.class));
        assertNotNull(databaseQuery.nativeQuery(mock(EntityManager.class), "SELECT 2", SampleUserTO.class));
    }
}
