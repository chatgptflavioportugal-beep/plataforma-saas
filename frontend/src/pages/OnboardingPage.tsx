import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { api } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

interface OnboardingForm {
  company_name: string
  company_slug: string
}

export function OnboardingPage() {
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } = useForm<OnboardingForm>()

  function handleNameChange(e: React.ChangeEvent<HTMLInputElement>) {
    const slug = e.target.value
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
    setValue('company_slug', slug)
  }

  async function onSubmit(data: OnboardingForm) {
    setServerError(null)
    try {
      await api.post('/api/v1/public/onboarding', {
        name: data.company_name,
        slug: data.company_slug,
      })
      navigate('/app/dashboard', { replace: true })
    } catch {
      setServerError('Erro ao criar empresa. Verifique se o slug está disponível.')
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-lg space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-gray-900">Configure sua empresa</h1>
          <p className="mt-2 text-gray-500">Você terá 14 dias grátis para experimentar</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-2xl bg-white p-8 shadow-sm border border-gray-100">
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

          {serverError && (
            <p className="text-sm text-red-600" role="alert">{serverError}</p>
          )}

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Criar empresa e começar trial
          </Button>
        </form>
      </div>
    </div>
  )
}
