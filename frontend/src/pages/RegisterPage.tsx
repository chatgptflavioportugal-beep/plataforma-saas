import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { supabase } from '@/lib/supabase'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'

interface RegisterForm {
  full_name: string
  email: string
  password: string
  password_confirm: string
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<RegisterForm>()

  async function onSubmit(data: RegisterForm) {
    setServerError(null)
    const { error } = await supabase.auth.signUp({
      email: data.email,
      password: data.password,
      options: {
        data: { full_name: data.full_name },
      },
    })
    if (error) {
      setServerError(error.message)
      return
    }
    setSuccess(true)
  }

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-md text-center space-y-4">
          <div className="text-5xl">✉️</div>
          <h2 className="text-2xl font-bold text-gray-900">Verifique seu email</h2>
          <p className="text-gray-500">
            Enviamos um link de confirmação. Após confirmar, você pode fazer login e criar sua empresa.
          </p>
          <Link to="/login" className="block font-medium text-primary-600 hover:underline">
            Ir para login
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-gray-900">Criar conta</h1>
          <p className="mt-2 text-gray-500">Comece seu trial grátis de 14 dias</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-2xl bg-white p-8 shadow-sm border border-gray-100">
          <Input
            label="Nome completo"
            autoComplete="name"
            error={errors.full_name?.message}
            {...register('full_name', { required: 'Nome obrigatório' })}
          />
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
            autoComplete="new-password"
            hint="Mínimo 8 caracteres"
            error={errors.password?.message}
            {...register('password', {
              required: 'Senha obrigatória',
              minLength: { value: 8, message: 'Mínimo 8 caracteres' },
            })}
          />
          <Input
            label="Confirmar senha"
            type="password"
            autoComplete="new-password"
            error={errors.password_confirm?.message}
            {...register('password_confirm', {
              required: 'Confirme a senha',
              validate: (v) => v === watch('password') || 'Senhas não coincidem',
            })}
          />

          {serverError && (
            <p className="text-sm text-red-600" role="alert">{serverError}</p>
          )}

          <Button type="submit" isLoading={isSubmitting} className="w-full">
            Criar conta grátis
          </Button>

          <p className="text-center text-sm text-gray-500">
            Já tem conta?{' '}
            <Link to="/login" className="font-medium text-primary-600 hover:underline">
              Fazer login
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
