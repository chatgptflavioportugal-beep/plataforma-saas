import pytest

from utils.text import normalize_filename, normalize_text


@pytest.mark.parametrize(
    ("filename", "expected"),
    [
        ("Te Agradeço Oh Pai.pdf", "te_agradeco_oh_pai.pdf"),
        ("a-ele-a glÓria.pdf", "a-ele-a_gloria.pdf"),
        ("Meu Arquivo Final.PDF", "meu_arquivo_final.pdf"),
        ("AÇÃO FINAL.pdf", "acao_final.pdf"),
        ("João da Silva.pdf", "joao_da_silva.pdf"),
        ("Relatório   Final.pdf", "relatorio_final.pdf"),
        ("Arquivo ÇÇÇ.pdf", "arquivo_ccc.pdf"),
        ("Teste__Final.pdf", "teste_final.pdf"),
    ],
)
def test_normalize_filename(filename, expected):
    assert normalize_filename(filename) == expected


def test_normalize_text_strips_leading_and_trailing_underscores():
    assert normalize_text("  Olá Mundo!  ") == "ola_mundo"


def test_normalize_text_preserves_hyphen_and_numbers():
    assert normalize_text("Plano-Pro 2024") == "plano-pro_2024"
