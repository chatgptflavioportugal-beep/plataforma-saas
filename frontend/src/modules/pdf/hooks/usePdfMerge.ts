import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/services/api'
import { useTenant } from '@/core/workspaces/TenantContext'
import type { PdfJob } from '@/shared/types'

export function usePdfJobs() {
  const { activeTenantId } = useTenant()

  return useQuery({
    queryKey: ['pdf-jobs', activeTenantId],
    queryFn: async () => {
      const { data } = await api.get<PdfJob[]>('/api/v1/pdf/jobs')
      return data
    },
    enabled: !!activeTenantId,
    staleTime: 30 * 1000,
  })
}

export function usePdfMerge() {
  const queryClient = useQueryClient()
  const { activeTenantId } = useTenant()

  return useMutation({
    mutationFn: async (files: { fileA: File; fileB: File }) => {
      const form = new FormData()
      form.append('file_a', files.fileA)
      form.append('file_b', files.fileB)

      const { data } = await api.post<PdfJob>('/api/v1/pdf/merge', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pdf-jobs', activeTenantId] })
    },
  })
}

export function usePdfDownload() {
  return useMutation({
    mutationFn: async (jobId: string) => {
      const response = await api.get(`/api/v1/pdf/jobs/${jobId}/download`, {
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([response.data as BlobPart]))
      const link = document.createElement('a')
      link.href = url
      link.download = `merged-${jobId}.pdf`
      link.click()
      window.URL.revokeObjectURL(url)
    },
  })
}
