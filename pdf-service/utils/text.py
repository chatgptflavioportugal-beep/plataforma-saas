import re
import unicodedata
from pathlib import Path


def normalize_text(text: str) -> str:
    """Normaliza texto para minúsculas, sem acentos, com espaços como `_`.

    Usado como base para slugs, nomes de arquivo/diretório e chaves de cache/log.
    """
    text = text.strip().lower()
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))

    text = re.sub(r"\s+", "_", text)
    text = re.sub(r"[^a-z0-9_-]", "", text)
    text = re.sub(r"_+", "_", text)
    text = text.strip("_")

    return text


def normalize_filename(filename: str) -> str:
    """Normaliza um nome de arquivo preservando a extensão."""
    path = Path(filename)
    stem = normalize_text(path.stem)
    suffix = path.suffix.lower()

    return f"{stem}{suffix}"
