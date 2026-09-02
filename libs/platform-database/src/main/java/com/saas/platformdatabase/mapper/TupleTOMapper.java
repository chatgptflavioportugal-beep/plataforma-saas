package com.saas.platformdatabase.mapper;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapta {@link Tuple} (retorno de {@code EntityManager.createNativeQuery(sql, Tuple.class)})
 * para {@code Map<String, Object>} e delega ao {@link GenericTOMapper}:
 *
 * <pre>Tuple -&gt; Map&lt;String,Object&gt; -&gt; GenericTOMapper -&gt; TO</pre>
 *
 * Cada elemento do {@link Tuple} de uma native query expoe como alias o nome da coluna
 * (ou o {@code AS} explicito, quando declarado) — nao e preciso apelidar toda coluna, so
 * quando o nome da coluna difere do {@code @Column} do TO (ex.: um cast como
 * {@code col::text AS col}).
 */
public final class TupleTOMapper {

    private TupleTOMapper() {
    }

    public static <T> T map(Tuple tuple, Class<T> targetType) {
        return GenericTOMapper.map(toMap(tuple), targetType);
    }

    public static <T> List<T> mapList(List<Tuple> tuples, Class<T> targetType) {
        List<T> result = new ArrayList<>(tuples.size());
        for (Tuple tuple : tuples) {
            result.add(map(tuple, targetType));
        }
        return result;
    }

    /** Exposto para quem precisa do resultado bruto (alias -&gt; valor) sem popular um TO. */
    public static Map<String, Object> toMap(Tuple tuple) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (TupleElement<?> element : tuple.getElements()) {
            String alias = element.getAlias();
            if (alias != null) {
                row.put(alias, tuple.get(alias));
            }
        }
        return row;
    }
}
