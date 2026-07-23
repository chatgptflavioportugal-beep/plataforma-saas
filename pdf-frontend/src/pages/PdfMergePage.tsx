import { useState, useRef } from 'react'
import { AxiosError } from 'axios'
import { usePdfMerge, usePdfJobs, usePdfDownload } from '@/hooks/usePdfMerge'
import { Button } from '@/components/Button'
import type { ApiError, PdfJob } from '@/shared/types'

interface PdfMergePageProps {
  /** Chamado quando o pdf-service recusa a operação por limite de plano (403). */
  onFeatureLocked?: (details: { requiredPlan?: string; feature?: string }) => void
}

export function PdfMergePage({ onFeatureLocked }: PdfMergePageProps) {
  const [fileA, setFileA] = useState<File | null>(null)
  const [fileB, setFileB] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refA = useRef<HTMLInputElement>(null)
  const refB = useRef<HTMLInputElement>(null)

  const { data: jobs = [], isLoading: jobsLoading } = usePdfJobs()
  const mergeMutation = usePdfMerge()
  const downloadMutation = usePdfDownload()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!fileA || !fileB) {
      setError('Selecione os dois arquivos PDF')
      return
    }

    setError(null)
    try {
      await mergeMutation.mutateAsync({ fileA, fileB })
      setFileA(null)
      setFileB(null)
      if (refA.current) refA.current.value = ''
      if (refB.current) refB.current.value = ''
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>
      if (axiosError.response?.status === 403) {
        onFeatureLocked?.({
          requiredPlan: axiosError.response.data.requiredPlan,
          feature: axiosError.response.data.feature,
        })
        return
      }
      setError('Erro ao processar merge. Tente novamente.')
    }
  }

  function statusBadge(status: PdfJob['status']) {
    const styles = {
      pending:    'bg-gray-100 text-gray-600',
      processing: 'bg-blue-100 text-blue-700',
      completed:  'bg-green-100 text-green-700',
      failed:     'bg-red-100 text-red-700',
    }
    const labels = {
      pending:    'Aguardando',
      processing: 'Processando',
      completed:  'Concluído',
      failed:     'Erro',
    }
    return (
      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${styles[status]}`}>
        {labels[status]}
      </span>
    )
  }

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Merge de PDFs</h1>
        <p className="text-gray-500 mt-1">Una dois PDFs em um único arquivo</p>
      </div>

      <form onSubmit={handleSubmit} className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">PDF A</label>
            <input
              ref={refA}
              type="file"
              accept=".pdf,application/pdf"
              aria-label="Selecionar PDF A"
              onChange={(e) => setFileA(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-primary-50 file:text-primary-700 hover:file:bg-primary-100 file:font-medium cursor-pointer"
            />
            {fileA && <p className="mt-1 text-xs text-gray-500 truncate">{fileA.name}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">PDF B</label>
            <input
              ref={refB}
              type="file"
              accept=".pdf,application/pdf"
              aria-label="Selecionar PDF B"
              onChange={(e) => setFileB(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-primary-50 file:text-primary-700 hover:file:bg-primary-100 file:font-medium cursor-pointer"
            />
            {fileB && <p className="mt-1 text-xs text-gray-500 truncate">{fileB.name}</p>}
          </div>
        </div>

        {error && (
          <p className="text-sm text-red-600" role="alert">{error}</p>
        )}

        <Button
          type="submit"
          isLoading={mergeMutation.isPending}
          disabled={!fileA || !fileB}
        >
          Fazer merge
        </Button>
      </form>

      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Histórico de merges</h2>
        {jobsLoading ? (
          <p className="text-gray-500 text-sm">Carregando...</p>
        ) : jobs.length === 0 ? (
          <p className="text-gray-400 text-sm">Nenhum merge realizado ainda.</p>
        ) : (
          <div className="rounded-xl bg-white border border-gray-100 shadow-sm overflow-hidden">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Arquivos</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Data</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td className="px-4 py-3 text-sm text-gray-900">
                      <span className="truncate max-w-xs block">{job.file_a_name} + {job.file_b_name}</span>
                    </td>
                    <td className="px-4 py-3">{statusBadge(job.status)}</td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {new Date(job.created_at).toLocaleDateString('pt-BR')}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {job.status === 'completed' && (
                        <Button
                          variant="secondary"
                          size="sm"
                          isLoading={downloadMutation.isPending}
                          onClick={() => downloadMutation.mutate(job)}
                        >
                          Baixar
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
