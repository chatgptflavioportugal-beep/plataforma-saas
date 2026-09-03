class DatabaseQueryException(Exception):
    """Falha ao executar a native query, ou mais de um resultado onde se esperava no máximo um."""


class DatabaseMappingException(Exception):
    """Falha ao popular o TO: coluna esperada ausente na linha, ou TO mal configurado."""
