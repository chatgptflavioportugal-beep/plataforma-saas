import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { supabase } from '@/lib/supabase'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

interface LoginForm {
  email: string
  password: string
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: Location })?.from?.pathname ?? '/app/dashboard'

  const [serverError, setServerError] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>()

  async function onSubmit(data: LoginForm) {
    setServerError(null)
    const { error } = await supabase.auth.signInWithPassword(data)
    if (error) {
      setServerError('Email ou senha inválidos.')
      return
    }
    navigate(from, { replace: true })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-gray-900">SaaS Platform</h1>
          <p className="mt-2 text-gray-500">Acesse sua conta</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-2xl bg-white p-8 shadow-sm border border-gray-100">
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
            <p className="text-sm text-red-600" role="alert">{serverError}</p>
          )}

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Entrar
          </Button>

          <p className="text-center text-sm text-gray-500">
            Não tem conta?{' '}
            <Link to="/register" className="font-medium text-primary-600 hover:underline">
              Criar conta grátis
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
