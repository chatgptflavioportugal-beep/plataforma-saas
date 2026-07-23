import { useMutation } from '@tanstack/react-query'
import { useModuleApi } from '@/contexts/ModuleApiContext'
import type { SendMessageResult } from '@/shared/types'

interface SendMessageInput {
  to: string
  message: string
}

export function useSendMessage() {
  const { moduleApi } = useModuleApi()

  return useMutation({
    mutationFn: async (input: SendMessageInput) => {
      const { data } = await moduleApi.post<SendMessageResult>('/whatsapp/messages', input)
      return data
    },
  })
}
