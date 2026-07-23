import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useModuleApi } from '@/contexts/ModuleApiContext'
import type { PdfJob } from '@/shared/types'

export function usePdfJobs() {
  const { moduleApi } = useModuleApi()

  return useQuery({
    queryKey: ['pdf-jobs'],
    queryFn: async () => {
      const { data } = await moduleApi.get<PdfJob[]>('/pdf/jobs')
      return data
    },
    staleTime: 30 * 1000,
  })
}

export function usePdfMerge() {
  const queryClient = useQueryClient()
  const { moduleApi } = useModuleApi()

  return useMutation({
    mutationFn: async (files: { fileA: File; fileB: File }) => {
      const form = new FormData()
      form.append('file_a', files.fileA)
      form.append('file_b', files.fileB)

      const { data } = await moduleApi.post<PdfJob>('/pdf/merge', form)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pdf-jobs'] })
    },
  })
}

export function usePdfDownload() {
  const { moduleApi } = useModuleApi()

  return useMutation({
    mutationFn: async (job: PdfJob) => {
      const response = await moduleApi.get(`/pdf/jobs/${job.id}/download`, {
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([response.data as BlobPart], { type: 'application/pdf' }))
      const link = document.createElement('a')
      link.href = url
      link.download = job.result_name ?? `merged-${job.id}.pdf`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    },
  })
}
