import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { supabase } from '@/core/auth/supabase'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

interface LoginForm {
  email: string
  password: string
}

/**
 * Login exclusivo do Frontend Admin — só email/senha. Login social (Google)
 * não é aceito para contas administrativas (ver SuperAdminGuard), então nem
 * é oferecido aqui.
 */
export function AdminLoginPage() {
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>()

  async function onSubmit(data: LoginForm) {
    setServerError(null)
    const { error } = await supabase.auth.signInWithPassword(data)
    if (error) {
      setServerError('Email ou senha inválidos.')
      return
    }
    navigate('/dashboard', { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-900 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-white">SaaS Platform</h1>
          <p className="mt-2 text-gray-400">Área administrativa</p>
        </div>

        <div className="rounded-2xl bg-gray-800 p-8 shadow-sm border border-gray-700 space-y-4">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email', { required: 'Email obrigatório' })}
            />
            <Input
              label="Senha"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password', { required: 'Senha obrigatória' })}
            />

            {serverError && (
              <p className="text-sm text-red-400" role="alert">{serverError}</p>
            )}

            <Button type="submit" isLoading={isSubmitting} className="w-full">
              Entrar
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
