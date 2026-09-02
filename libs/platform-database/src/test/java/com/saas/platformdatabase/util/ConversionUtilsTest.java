package com.saas.platformdatabase.util;

import com.saas.platformdatabase.exception.DatabaseMappingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversionUtilsTest {

    private enum Status { ACTIVE, INACTIVE }

    @Test
    void nullValueConvertsToNull() {
        assertNull(ConversionUtils.convert(null, String.class));
    }

    @Test
    void valueAlreadyOfTargetTypePassesThrough() {
        assertEquals("abc", ConversionUtils.convert("abc", String.class));
    }

    @Test
    void oracleBigDecimalConvertsToLongIntegerAndBoolean() {
        assertEquals(10L, ConversionUtils.convert(new BigDecimal("10"), Long.class));
        assertEquals(10, ConversionUtils.convert(new BigDecimal("10"), Integer.class));
        assertEquals(Boolean.TRUE, ConversionUtils.convert(BigDecimal.ONE, Boolean.class));
        assertEquals(Boolean.FALSE, ConversionUtils.convert(BigDecimal.ZERO, Boolean.class));
    }

    @Test
    void stringConvertsToUUID() {
        UUID id = UUID.randomUUID();
        assertEquals(id, ConversionUtils.convert(id.toString(), UUID.class));
    }

    @Test
    void timestampConvertsToLocalDateAndLocalDateTime() {
        Timestamp timestamp = Timestamp.valueOf("2026-09-01 10:30:00");
        assertEquals(LocalDate.of(2026, 9, 1), ConversionUtils.convert(timestamp, LocalDate.class));
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 30, 0), ConversionUtils.convert(timestamp, LocalDateTime.class));
    }

    @Test
    void stringConvertsToEnum() {
        assertEquals(Status.ACTIVE, ConversionUtils.convert("ACTIVE", Status.class));
    }

    @Test
    void timestampConvertsToOffsetDateTimeInUtc() {
        // Timestamp.from(Instant) fixa o instante diretamente, sem depender do fuso
        // horario padrao da JVM (diferente de Timestamp.valueOf(String), que interpreta
        // a string no fuso local) — assim o teste da mesma resposta em qualquer maquina.
        OffsetDateTime expected = OffsetDateTime.of(2026, 9, 1, 10, 30, 0, 0, ZoneOffset.UTC);
        Timestamp timestamp = Timestamp.from(expected.toInstant());

        assertEquals(expected, ConversionUtils.convert(timestamp, OffsetDateTime.class));
    }

    @Test
    void utilDateConvertsToLocalDate() {
        LocalDate expected = LocalDate.of(2026, 9, 1);
        Date date = Date.from(expected.atStartOfDay(ZoneId.systemDefault()).toInstant());

        assertEquals(expected, ConversionUtils.convert(date, LocalDate.class));
    }

    @Test
    void unsupportedTargetTypeThrowsDatabaseMappingException() {
        assertThrows(DatabaseMappingException.class, () -> ConversionUtils.convert("x", Thread.class));
    }
}
