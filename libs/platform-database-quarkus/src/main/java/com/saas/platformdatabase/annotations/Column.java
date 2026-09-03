package com.saas.platformdatabase.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Liga um campo de um TO ao alias de coluna retornado por uma native query.
 * Campos sem esta anotacao sao ignorados pelo {@code GenericTOMapper}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /** Alias da coluna na query (comparado sem diferenciar maiusculas/minusculas). */
    String name();
}
