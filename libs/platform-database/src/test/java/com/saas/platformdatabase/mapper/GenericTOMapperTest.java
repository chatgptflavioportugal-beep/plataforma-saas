package com.saas.platformdatabase.mapper;

import com.saas.platformdatabase.exception.DatabaseMappingException;
import com.saas.platformdatabase.support.SampleAuditedUserTO;
import com.saas.platformdatabase.support.SampleUserTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenericTOMapperTest {

    @Test
    void mapsAnnotatedFieldsByAlias() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId.toString());
        row.put("email", "user@example.com");
        row.put("active", Boolean.TRUE);
        row.put("login_count", 42L);
        row.put("score", new BigDecimal("9.5"));

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertEquals(userId, to.getUserId());
        assertEquals("user@example.com", to.getEmail());
        assertEquals(Boolean.TRUE, to.getActive());
        assertEquals(42L, to.getLoginCount());
        assertEquals(new BigDecimal("9.5"), to.getScore());
    }

    @Test
    void aliasLookupIsCaseInsensitive_forOracleStyleUppercaseAliases() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("USER_ID", UUID.randomUUID().toString());
        row.put("EMAIL", "oracle@example.com");
        row.put("ACTIVE", BigDecimal.ONE);

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertEquals("oracle@example.com", to.getEmail());
        assertEquals(Boolean.TRUE, to.getActive());
    }

    @Test
    void fieldsWithoutColumnAnnotationAreIgnored() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ignored", "should not be set");

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertNull(to.getIgnored());
    }

    @Test
    void missingAliasLeavesFieldNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", "only-email@example.com");

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertEquals("only-email@example.com", to.getEmail());
        assertNull(to.getUserId());
    }

    @Test
    void mapListMapsEveryRow() {
        Map<String, Object> row1 = Map.of("email", "a@example.com");
        Map<String, Object> row2 = Map.of("email", "b@example.com");

        List<SampleUserTO> tos = GenericTOMapper.mapList(List.of(row1, row2), SampleUserTO.class);

        assertEquals(2, tos.size());
        assertEquals("a@example.com", tos.get(0).getEmail());
        assertEquals("b@example.com", tos.get(1).getEmail());
    }

    @Test
    void unsupportedConversionThrowsDatabaseMappingException() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("login_count", "not-a-number");

        assertThrows(DatabaseMappingException.class, () -> GenericTOMapper.map(row, SampleUserTO.class));
    }

    @Test
    void columnWithoutAnyMatchingFieldInTOIsIgnored() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", "user@example.com");
        row.put("some_column_the_to_does_not_declare_at_all", "whatever");

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertEquals("user@example.com", to.getEmail());
    }

    @Test
    void explicitNullValueMapsToNullWithoutThrowing() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", null);
        row.put("login_count", 5L);

        SampleUserTO to = GenericTOMapper.map(row, SampleUserTO.class);

        assertNull(to.getEmail());
        assertEquals(5L, to.getLoginCount());
    }

    @Test
    void inheritedColumnAnnotationsAreMapped() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("email", "inherited@example.com");
        row.put("created_at", java.sql.Timestamp.valueOf("2026-09-01 08:00:00"));

        SampleAuditedUserTO to = GenericTOMapper.map(row, SampleAuditedUserTO.class);

        assertEquals("inherited@example.com", to.getEmail());
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0, 0), to.getCreatedAt());
    }
}
