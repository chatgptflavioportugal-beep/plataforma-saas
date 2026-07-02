import io

from pypdf import PdfReader, PdfWriter


def merge_pdfs(content_a: bytes, content_b: bytes) -> bytes:
    reader_a = PdfReader(io.BytesIO(content_a))
    reader_b = PdfReader(io.BytesIO(content_b))

    writer = PdfWriter()

    for page in reader_a.pages:
        writer.add_page(page)

    for page in reader_b.pages:
        writer.add_page(page)

    output = io.BytesIO()
    writer.write(output)
    output.seek(0)
    return output.read()
