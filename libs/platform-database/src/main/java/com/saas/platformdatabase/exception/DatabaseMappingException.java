package com.saas.platformdatabase.exception;

/** Falha ao mapear o resultado de uma native query (Tuple/Map) para um TO. */
public class DatabaseMappingException extends RuntimeException {

    public DatabaseMappingException(String message) {
        super(message);
    }

    public DatabaseMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
