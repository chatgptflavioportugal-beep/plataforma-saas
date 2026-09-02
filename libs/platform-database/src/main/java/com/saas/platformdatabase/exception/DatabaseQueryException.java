package com.saas.platformdatabase.exception;

/**
 * Falha na execucao de uma native query: SQL invalido, parametro incompativel com a coluna,
 * ou a consulta retornou mais de um registro onde {@code getOptionalResult()}/
 * {@code getSingleResult()} esperavam no maximo um.
 */
public class DatabaseQueryException extends RuntimeException {

    public DatabaseQueryException(String message) {
        super(message);
    }

    public DatabaseQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
