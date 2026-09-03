package com.saas.platformdatabase.mapper;

import com.saas.platformdatabase.annotations.Column;
import com.saas.platformdatabase.exception.DatabaseMappingException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection sobre um TO, calculada uma unica vez por {@link Class} e reaproveitada pelo
 * {@link GenericTOMapper} para todas as linhas de uma consulta (ver
 * {@code GenericTOMapper.METADATA_CACHE}) — evita reler as anotacoes {@link Column} e
 * refazer {@code setAccessible} a cada registro.
 *
 * <p>Percorre a hierarquia de superclasses (ate {@link Object}) para que um TO que estenda
 * outro TO tambem tenha os campos {@code @Column} da superclasse mapeados.
 */
final class TOMetadata<T> {

    private final Constructor<T> constructor;
    private final List<FieldMapping> fields;

    private TOMetadata(Constructor<T> constructor, List<FieldMapping> fields) {
        this.constructor = constructor;
        this.fields = fields;
    }

    static <T> TOMetadata<T> of(Class<T> type) {
        Constructor<T> constructor;
        try {
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new DatabaseMappingException(
                    "TO " + type.getName() + " precisa de um construtor sem argumentos", e);
        }

        List<FieldMapping> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    field.setAccessible(true);
                    fields.add(new FieldMapping(field, column.name()));
                }
            }
        }
        return new TOMetadata<>(constructor, List.copyOf(fields));
    }

    T newInstance() {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DatabaseMappingException(
                    "Nao foi possivel instanciar " + constructor.getDeclaringClass().getName(), e);
        }
    }

    List<FieldMapping> fields() {
        return fields;
    }

    record FieldMapping(Field field, String columnName) {
    }
}
