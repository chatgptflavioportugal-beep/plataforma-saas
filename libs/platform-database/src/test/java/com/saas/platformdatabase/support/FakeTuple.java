package com.saas.platformdatabase.support;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Duplo de teste de {@link Tuple} baseado em um Map — evita subir um EntityManager real. */
public class FakeTuple implements Tuple {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public FakeTuple with(String alias, Object value) {
        values.put(alias, value);
        return this;
    }

    @Override
    public <X> X get(TupleElement<X> tupleElement) {
        return get(tupleElement.getAlias(), tupleElement.getJavaType());
    }

    @Override
    public Object get(String alias) {
        return values.get(alias);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X get(String alias, Class<X> type) {
        return (X) values.get(alias);
    }

    @Override
    public Object get(int i) {
        return toArray()[i];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X get(int i, Class<X> type) {
        return (X) toArray()[i];
    }

    @Override
    public Object[] toArray() {
        return values.values().toArray();
    }

    @Override
    public List<TupleElement<?>> getElements() {
        List<TupleElement<?>> elements = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Class<?> javaType = entry.getValue() == null ? Object.class : entry.getValue().getClass();
            elements.add(new FakeTupleElement(entry.getKey(), javaType));
        }
        return elements;
    }

    /**
     * Implementa {@code TupleElement<Object>} independentemente do tipo real do valor —
     * so o alias importa para o {@code TupleTOMapper} (que nunca chama {@code getJavaType()}).
     */
    private static final class FakeTupleElement implements TupleElement<Object> {
        private final String alias;
        private final Class<?> javaType;

        private FakeTupleElement(String alias, Class<?> javaType) {
            this.alias = alias;
            this.javaType = javaType;
        }

        @Override
        public String getAlias() {
            return alias;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<Object> getJavaType() {
            return (Class<Object>) javaType;
        }
    }
}
