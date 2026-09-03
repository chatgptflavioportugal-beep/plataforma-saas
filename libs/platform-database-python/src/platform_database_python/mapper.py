import dataclasses
from collections.abc import Iterable, Mapping
from typing import TypeVar

from .exceptions import DatabaseMappingException

T = TypeVar("T")

_metadata_cache: dict[type, list[tuple[str, str]]] = {}


def column(name: str):
    """Equivalente a @Column(name=...) do lado Java: liga um campo do TO ao
    alias/nome de coluna esperado na linha retornada pela query."""
    return dataclasses.field(metadata={"column": name})


def _metadata_for(to_type: type[T]) -> list[tuple[str, str]]:
    metadata = _metadata_cache.get(to_type)
    if metadata is None:
        metadata = [
            (f.name, f.metadata.get("column", f.name))
            for f in dataclasses.fields(to_type)
        ]
        _metadata_cache[to_type] = metadata
    return metadata


def row_to_object(row: Mapping, to_type: type[T]) -> T:
    """Mapeia uma linha (asyncpg.Record ou dict) para o TO informado. O nome da
    coluna é comparado sem diferenciar maiúsculas/minúsculas."""
    row_by_lower_key = {str(key).lower(): value for key, value in dict(row).items()}
    kwargs = {}
    for field_name, column_name in _metadata_for(to_type):
        lookup_key = column_name.lower()
        if lookup_key not in row_by_lower_key:
            raise DatabaseMappingException(
                f"Coluna '{column_name}' esperada por {to_type.__name__}.{field_name} "
                f"não encontrada na linha retornada. Colunas disponíveis: {list(dict(row).keys())}"
            )
        kwargs[field_name] = row_by_lower_key[lookup_key]

    try:
        return to_type(**kwargs)
    except TypeError as e:
        raise DatabaseMappingException(
            f"Falha ao construir {to_type.__name__} a partir da linha: {e}"
        ) from e


def rows_to_objects(rows: Iterable[Mapping], to_type: type[T]) -> list[T]:
    return [row_to_object(row, to_type) for row in rows]
