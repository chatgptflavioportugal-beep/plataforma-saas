import { useState } from 'react'
import { AxiosError } from 'axios'
import { useSendMessage } from '@/hooks/useSendMessage'
import { Button } from '@/components/Button'
import type { ApiError } from '@/shared/types'

/**
 * Estrutura básica do módulo WhatsApp — o whatsapp-service ainda só tem o
 * esqueleto de envio (POST /whatsapp/messages devolve 501 Not Implemented).
 * Esta página existe para validar o fluxo Host → Module Federation →
 * whatsapp-service ponta a ponta antes do módulo ganhar funcionalidade real.
 */
export function WhatsAppPage() {
  const [to, setTo] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)

  const sendMutation = useSendMessage()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await sendMutation.mutateAsync({ to, message })
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>
      if (axiosError.response?.status === 501) {
        setError('Envio de mensagens ainda não implementado neste ambiente.')
        return
      }
      setError('Erro ao enviar mensagem. Tente novamente.')
    }
  }

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">WhatsApp</h1>
        <p className="text-gray-500 mt-1">Envio de mensagens via WhatsApp</p>
      </div>

      <form onSubmit={handleSubmit} className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Número (com DDI/DDD)</label>
          <input
            type="text"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            placeholder="5511999999999"
            className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Mensagem</label>
          <textarea
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={4}
            className="block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
          />
        </div>

        {error && (
          <p className="text-sm text-red-600" role="alert">{error}</p>
        )}

        <Button type="submit" isLoading={sendMutation.isPending} disabled={!to || !message}>
          Enviar
        </Button>
      </form>
    </div>
  )
}
