import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import type { AxiosError } from 'axios'
import { api, setActiveTenant } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

type Step = 'choose' | 'business-form'

interface BusinessForm {
  company_name: string
  company_slug: string
}

function PersonIcon() {
  return (
    <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  )
}

function BuildingIcon() {
  return (
    <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round"
        d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
    </svg>
  )
}

export function OnboardingPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const cameFromSelector = (location.state as { type?: string } | null)?.type === 'business'
  const [step, setStep] = useState<Step>(cameFromSelector ? 'business-form' : 'choose')
  const [error, setError] = useState<string | null>(null)
  const [isCreatingIndividual, setIsCreatingIndividual] = useState(false)

  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm<BusinessForm>()

  async function handleCreateIndividual() {
    setIsCreatingIndividual(true)
    setError(null)
    try {
      const { data } = await api.post<{ id: string }>('/api/v1/public/individual-tenant')
      setActiveTenant(data.id)
      window.location.href = '/app/dashboard'
    } catch (err) {
      const axiosErr = err as AxiosError<{ error?: string }>
      if (!axiosErr.response) {
        setError('Sem resposta do servidor. Verifique se o backend está rodando.')
      } else {
        const status = axiosErr.response.status
        const msg = axiosErr.response.data?.error
        setError(`Erro ${status}${msg ? ': ' + msg : ''} ao criar perfil individual.`)
      }
      setIsCreatingIndividual(false)
    }
  }

  function handleNameChange(e: React.ChangeEvent<HTMLInputElement>) {
    const slug = e.target.value
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
    setValue('company_slug', slug)
  }

  async function onSubmitBusiness(data: BusinessForm) {
    setError(null)
    try {
      const res = await api.post<{ id: string }>('/api/v1/public/onboarding', {
        name: data.company_name,
        slug: data.company_slug,
      })
      setActiveTenant(res.data.id)
      window.location.href = '/app/dashboard'
    } catch (err) {
      const axiosErr = err as AxiosError<{ error?: string; message?: string }>
      if (!axiosErr.response) {
        setError('Não foi possível conectar ao servidor. Verifique se o backend está rodando.')
        return
      }
      const status = axiosErr.response.status
      const body = axiosErr.response.data
      if (status === 409) {
        setError('Este identificador (slug) já está em uso. Escolha outro.')
        return
      }
      setError(body?.message ?? body?.error ?? `Erro ${status} ao criar empresa.`)
    }
  }

  function handleBack() {
    setError(null)
    if (cameFromSelector) {
      navigate('/select-context', { replace: true })
    } else {
      setStep('choose')
    }
  }

  if (step === 'choose') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-lg space-y-8">
          <div className="text-center">
            <h1 className="text-3xl font-bold text-gray-900">Criar seu primeiro perfil</h1>
            <p className="mt-2 text-gray-500">Como você pretende usar a plataforma?</p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <button
              onClick={handleCreateIndividual}
              disabled={isCreatingIndividual}
              className="flex flex-col items-center gap-4 rounded-2xl border-2 border-gray-200 bg-white p-8 text-center shadow-sm hover:border-primary-500 hover:shadow-md transition-all disabled:cursor-not-allowed disabled:opacity-60"
            >
              <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary-50 text-primary-600">
                {isCreatingIndividual ? (
                  <div className="h-7 w-7 animate-spin rounded-full border-2 border-primary-600 border-t-transparent" />
                ) : (
                  <PersonIcon />
                )}
              </div>
              <div>
                <p className="text-base font-semibold text-gray-900">Perfil Individual</p>
                <p className="mt-1 text-sm text-gray-400">Para uso pessoal · 14 dias grátis</p>
              </div>
            </button>

            <button
              onClick={() => { setError(null); setStep('business-form') }}
              disabled={isCreatingIndividual}
              className="flex flex-col items-center gap-4 rounded-2xl border-2 border-gray-200 bg-white p-8 text-center shadow-sm hover:border-indigo-500 hover:shadow-md transition-all disabled:cursor-not-allowed disabled:opacity-60"
            >
              <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                <BuildingIcon />
              </div>
              <div>
                <p className="text-base font-semibold text-gray-900">Perfil Empresarial</p>
                <p className="mt-1 text-sm text-gray-400">Para sua empresa · 14 dias grátis</p>
              </div>
            </button>
          </div>

          {error && <p className="text-center text-sm text-red-600">{error}</p>}
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-lg space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-gray-900">Criar conta empresarial</h1>
          <p className="mt-2 text-gray-500">Você terá 14 dias grátis para experimentar.</p>
        </div>

        <form
          onSubmit={handleSubmit(onSubmitBusiness)}
          className="space-y-4 rounded-2xl border border-gray-100 bg-white p-8 shadow-sm"
        >
          <Input
            label="Nome da empresa"
            placeholder="Minha Empresa Ltda"
            error={errors.company_name?.message}
            {...register('company_name', {
              required: 'Nome da empresa obrigatório',
              onChange: handleNameChange,
            })}
          />
          <Input
            label="Identificador único (slug)"
            placeholder="minha-empresa"
            hint="Usado na URL e identificação interna"
            error={errors.company_slug?.message}
            {...register('company_slug', {
              required: 'Slug obrigatório',
              pattern: { value: /^[a-z0-9-]+$/, message: 'Apenas letras minúsculas, números e hífen' },
            })}
          />

          {error && <p className="text-sm text-red-600" role="alert">{error}</p>}

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Criar empresa e começar trial
          </Button>
        </form>

        <div className="text-center">
          <button onClick={handleBack} className="text-sm text-gray-400 hover:text-gray-600">
            ← Voltar
          </button>
        </div>
      </div>
    </div>
  )
}
