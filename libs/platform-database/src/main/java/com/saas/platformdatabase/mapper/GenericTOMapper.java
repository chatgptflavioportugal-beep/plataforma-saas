package com.saas.platformdatabase.mapper;

import com.saas.platformdatabase.annotations.Column;
import com.saas.platformdatabase.exception.DatabaseMappingException;
import com.saas.platformdatabase.util.ConversionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Popula qualquer TO por reflection a partir de uma linha generica ({@code Map<String,
 * Object>}: alias de coluna -&gt; valor), sem depender de {@code SQLResultSetMapping} nem de
 * {@code Object[]} posicional.
 *
 * <p>Apenas campos anotados com {@link Column} sao considerados (incluindo campos herdados de
 * uma superclasse); o alias e comparado sem diferenciar maiusculas/minusculas (Oracle costuma
 * devolver aliases em maiusculo, PostgreSQL em minusculo). O TO precisa de um construtor sem
 * argumentos.
 *
 * <p>A reflection sobre cada {@link Class} (campos anotados, construtor) e feita uma unica vez
 * e cacheada em {@link TOMetadata} — uma consulta com milhares de linhas nao repete o scan de
 * anotacoes a cada linha, so a conversao de valor por campo.
 */
public final class GenericTOMapper {

    private static final ConcurrentHashMap<Class<?>, TOMetadata<?>> METADATA_CACHE = new ConcurrentHashMap<>();

    private GenericTOMapper() {
    }

    public static <T> T map(Map<String, Object> row, Class<T> targetType) {
        TOMetadata<T> metadata = metadataFor(targetType);

        Map<String, Object> caseInsensitiveRow = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitiveRow.putAll(row);

        T instance = metadata.newInstance();
        for (TOMetadata.FieldMapping mapping : metadata.fields()) {
            if (!caseInsensitiveRow.containsKey(mapping.columnName())) {
                continue;
            }
            Object rawValue = caseInsensitiveRow.get(mapping.columnName());
            Object convertedValue = ConversionUtils.convert(rawValue, mapping.field().getType());
            setField(instance, mapping.field(), convertedValue);
        }
        return instance;
    }

    public static <T> List<T> mapList(List<Map<String, Object>> rows, Class<T> targetType) {
        List<T> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(map(row, targetType));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> TOMetadata<T> metadataFor(Class<T> targetType) {
        return (TOMetadata<T>) METADATA_CACHE.computeIfAbsent(targetType, TOMetadata::of);
    }

    private static void setField(Object instance, Field field, Object value) {
        try {
            field.set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new DatabaseMappingException(
                    "Nao foi possivel atribuir o campo '" + field.getName() + "' em "
                            + instance.getClass().getName(), e);
        }
    }
}
