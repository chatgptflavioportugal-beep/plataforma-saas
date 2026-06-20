import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTenant } from '@/core/workspaces/TenantContext'
import { useModule } from '@/core/modules/ModuleContext'
import type { PdfJob } from '@/shared/types'

export function usePdfJobs() {
  const { activeTenantId } = useTenant()
  const { moduleApi } = useModule()

  return useQuery({
    queryKey: ['pdf-jobs', activeTenantId],
    queryFn: async () => {
      const { data } = await moduleApi!.get<PdfJob[]>('/api/v1/modules/pdf/jobs')
      return data
    },
    enabled: !!activeTenantId && !!moduleApi,
    staleTime: 30 * 1000,
  })
}

export function usePdfMerge() {
  const queryClient = useQueryClient()
  const { activeTenantId } = useTenant()
  const { moduleApi } = useModule()

  return useMutation({
    mutationFn: async (files: { fileA: File; fileB: File }) => {
      const form = new FormData()
      form.append('file_a', files.fileA)
      form.append('file_b', files.fileB)

      const { data } = await moduleApi!.post<PdfJob>('/api/v1/modules/pdf/merge', form, {
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
  const { moduleApi } = useModule()

  return useMutation({
    mutationFn: async (jobId: string) => {
      const response = await moduleApi!.get(`/api/v1/modules/pdf/jobs/${jobId}/download`, {
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
