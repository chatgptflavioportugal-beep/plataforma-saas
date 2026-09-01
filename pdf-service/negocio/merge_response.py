from schemas.pdf_job import PdfJobOut, job_to_out


def build_response(job: dict) -> PdfJobOut:
    return job_to_out(job)
