package com.saas.platformdatabase.util;

import com.saas.platformdatabase.exception.DatabaseMappingException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

/**
 * Conversoes de tipo entre o valor bruto retornado por uma native query (Oracle ou
 * PostgreSQL, via JDBC/Hibernate) e o tipo declarado no campo do TO.
 *
 * <p>Oracle costuma devolver {@link BigDecimal} para qualquer coluna numerica (mesmo
 * NUMBER(1) usado como boolean); PostgreSQL devolve o tipo Java correspondente ao tipo
 * de coluna nativo. Os dois casos sao tratados aqui para que o TO nao precise saber de
 * qual banco o valor veio.
 */
public final class ConversionUtils {

    private ConversionUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        try {
            if (targetType == String.class) {
                return (T) value.toString();
            }
            if (targetType == Long.class || targetType == long.class) {
                return (T) toLong(value);
            }
            if (targetType == Integer.class || targetType == int.class) {
                return (T) toInteger(value);
            }
            if (targetType == Double.class || targetType == double.class) {
                return (T) toDouble(value);
            }
            if (targetType == Float.class || targetType == float.class) {
                return (T) toFloat(value);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return (T) toBoolean(value);
            }
            if (targetType == BigDecimal.class) {
                return (T) toBigDecimal(value);
            }
            if (targetType == UUID.class) {
                return (T) toUUID(value);
            }
            if (targetType == LocalDate.class) {
                return (T) toLocalDate(value);
            }
            if (targetType == LocalDateTime.class) {
                return (T) toLocalDateTime(value);
            }
            if (targetType == OffsetDateTime.class) {
                return (T) toOffsetDateTime(value);
            }
            if (targetType == Timestamp.class) {
                return (T) toTimestamp(value);
            }
            if (targetType == Date.class) {
                return (T) toDate(value);
            }
            if (targetType.isEnum()) {
                return (T) toEnum(value, (Class<? extends Enum>) targetType);
            }
        } catch (DatabaseMappingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DatabaseMappingException(
                    "Nao foi possivel converter o valor '" + value + "' (" + value.getClass().getName()
                            + ") para " + targetType.getName(), e);
        }

        throw new DatabaseMappingException(
                "Conversao nao suportada: " + value.getClass().getName() + " -> " + targetType.getName());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString().trim());
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString().trim());
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString().trim());
    }

    private static Float toFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return Float.parseFloat(value.toString().trim());
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        // Oracle: NUMBER(1) usado como boolean chega como BigDecimal/Number (0/1).
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        if ("1".equals(text) || "S".equalsIgnoreCase(text) || "Y".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "N".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(text);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString().trim());
    }

    private static UUID toUUID(Object value) {
        return UUID.fromString(value.toString().trim());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(value.toString().trim());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().trim());
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Date date) {
            return date.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString().trim());
    }

    private static Timestamp toTimestamp(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return Timestamp.valueOf(dateTime);
        }
        if (value instanceof LocalDate date) {
            return Timestamp.valueOf(date.atStartOfDay());
        }
        if (value instanceof Date date) {
            return new Timestamp(date.getTime());
        }
        return Timestamp.valueOf(value.toString().trim());
    }

    private static Date toDate(Object value) {
        if (value instanceof Timestamp timestamp) {
            return new Date(timestamp.getTime());
        }
        if (value instanceof LocalDateTime dateTime) {
            return new Date(Timestamp.valueOf(dateTime).getTime());
        }
        if (value instanceof LocalDate date) {
            return new Date(Timestamp.valueOf(date.atStartOfDay()).getTime());
        }
        throw new DatabaseMappingException("Nao foi possivel converter " + value.getClass().getName() + " para java.util.Date");
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E toEnum(Object value, Class<E> enumType) {
        return Enum.valueOf(enumType, value.toString().trim());
    }
}
