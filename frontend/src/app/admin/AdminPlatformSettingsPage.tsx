import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { subscriptionApi } from '@/shared/services/subscriptionApi'
import { useAuth } from '@/core/auth/AuthContext'
import type { PlatformSetting } from '@/shared/types'

export function AdminPlatformSettingsPage() {
  const qc = useQueryClient()
  const { hasAdminPermission } = useAuth()
  const canEdit = hasAdminPermission('admin.settings.edit')

  const [cooldownDays, setCooldownDays] = useState('')
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: settings = [], isLoading } = useQuery({
    queryKey: ['admin-platform-settings'],
    queryFn: async () => {
      const { data } = await subscriptionApi.get<PlatformSetting[]>('/api/v1/admin/platform-settings')
      return data
    },
  })

  const cooldownSetting = settings.find((s) => s.key === 'trial_reuse_cooldown_days')

  useEffect(() => {
    if (cooldownSetting && cooldownDays === '') {
      setCooldownDays(cooldownSetting.value)
    }
  }, [cooldownSetting, cooldownDays])

  const saveMutation = useMutation({
    mutationFn: (value: string) => subscriptionApi.put('/api/v1/admin/platform-settings/trial_reuse_cooldown_days', { value }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-platform-settings'] })
      setError(null)
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { error?: string } } })?.response?.data?.error
      setError(msg ?? 'Erro ao salvar configuração')
    },
  })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Configurações</h1>
        <p className="text-sm text-gray-400 mt-0.5">Configurações gerais da plataforma</p>
      </div>

      <div className="rounded-xl bg-gray-800/60 border border-gray-700 p-6 max-w-xl space-y-4">
        <div>
          <h3 className="text-sm font-semibold text-white">Trial</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            {cooldownSetting?.description ?? 'Dias mínimos entre o fim de um Trial e a liberação de um novo Trial do mesmo módulo, para o mesmo perfil.'}
          </p>
        </div>

        {isLoading ? (
          <p className="text-sm text-gray-400">Carregando…</p>
        ) : (
          <div className="flex items-end gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-400 mb-1">Dias de carência para reutilização de Trial</label>
              <input type="number" min="0" value={cooldownDays}
                onChange={(e) => setCooldownDays(e.target.value)}
                disabled={!canEdit}
                className="w-40 rounded-lg bg-gray-700 border border-gray-600 px-3 py-2 text-sm text-white focus:outline-none focus:border-blue-500 disabled:opacity-50" />
            </div>
            {canEdit && (
              <button type="button"
                onClick={() => saveMutation.mutate(cooldownDays)}
                disabled={saveMutation.isPending || cooldownDays.trim() === ''}
                className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-500 disabled:opacity-50 transition-colors">
                {saveMutation.isPending ? 'Salvando…' : 'Salvar'}
              </button>
            )}
          </div>
        )}

        {saved && <p className="text-xs text-green-400">Configuração salva.</p>}
        {error && (
          <div className="rounded-lg bg-red-900/30 border border-red-700 px-4 py-3 text-sm text-red-300">{error}</div>
        )}
        {cooldownSetting?.updatedAt && (
          <p className="text-xs text-gray-500">Última atualização: {new Date(cooldownSetting.updatedAt).toLocaleString('pt-BR')}</p>
        )}
      </div>
    </div>
  )
}
