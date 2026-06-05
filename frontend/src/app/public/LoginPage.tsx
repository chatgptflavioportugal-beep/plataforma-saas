import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { supabase } from '@/core/auth/supabase'
import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

interface LoginForm {
  email: string
  password: string
}

function GoogleIcon() {
  return (
    <svg className="h-5 w-5" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
      <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
      <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05" />
      <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
    </svg>
  )
}

async function resolveDestination(userId: string): Promise<string> {
  const { data } = await supabase
    .from('user_profiles')
    .select('system_role')
    .eq('id', userId)
    .single()
  const role = data?.system_role
  return (role === 'SUPER_ADMIN' || role === 'ADMIN_USER') ? '/admin/dashboard' : '/app/dashboard'
}

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnUrl = searchParams.get('returnUrl')
  const [serverError, setServerError] = useState<string | null>(null)
  const [googleLoading, setGoogleLoading] = useState(false)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>()

  async function onSubmit(data: LoginForm) {
    setServerError(null)
    const { error, data: authData } = await supabase.auth.signInWithPassword(data)
    if (error) {
      setServerError('Email ou senha inválidos.')
      return
    }
    const destination = returnUrl ?? await resolveDestination(authData.user!.id)
    navigate(destination, { replace: true })
  }

  async function signInWithGoogle() {
    setGoogleLoading(true)
    if (returnUrl) sessionStorage.setItem('auth_return_url', returnUrl)
    await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-gray-900">SaaS Platform</h1>
          <p className="mt-2 text-gray-500">Acesse sua conta</p>
        </div>

        <div className="rounded-2xl bg-white p-8 shadow-sm border border-gray-100 space-y-4">
          <button
            type="button"
            onClick={signInWithGoogle}
            disabled={googleLoading}
            className="flex w-full items-center justify-center gap-3 rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60 transition-colors"
          >
            <GoogleIcon />
            {googleLoading ? 'Redirecionando...' : 'Entrar com Google'}
          </button>

          <div className="flex items-center gap-3">
            <div className="flex-1 border-t border-gray-200" />
            <span className="text-xs text-gray-400">ou</span>
            <div className="flex-1 border-t border-gray-200" />
          </div>

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
              <p className="text-sm text-red-600" role="alert">{serverError}</p>
            )}

            <Button type="submit" isLoading={isSubmitting} className="w-full">
              Entrar
            </Button>
          </form>

          <p className="text-center text-sm text-gray-500">
            Não tem conta?{' '}
            <Link to="/register" className="font-medium text-primary-600 hover:underline">
              Criar conta grátis
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
