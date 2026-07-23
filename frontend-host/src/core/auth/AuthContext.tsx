import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from './supabase'
import { queryClient } from '@/shared/services/query-client'
import type { UserProfile } from '@/shared/types'

interface AuthContextValue {
  session: Session | null
  user: User | null
  profile: UserProfile | null
  isLoading: boolean
  /** true quando a conta logada é administrativa (SUPER_ADMIN/ADMIN_USER) — não pertence à área de clientes. */
  isAdminAccount: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Camada de sessão Supabase do Front Host. Diferente do frontend monolítico
 * original, este contexto NUNCA carrega permissões administrativas nem chama
 * o Admin Service — a área administrativa é um app totalmente separado
 * (frontend-admin). Aqui só interessa saber se a conta É administrativa, para
 * o ClientAreaGuard poder bloquear o acesso à área de clientes.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session)
      if (session?.user) {
        loadProfile(session.user.id)
      } else {
        setProfile(null)
        setIsLoading(false)
        queryClient.clear()
      }
    })

    return () => subscription.unsubscribe()
  }, [])

  async function loadProfile(userId: string) {
    const { data } = await supabase
      .from('user_profiles')
      .select('*')
      .eq('id', userId)
      .single()

    setProfile(data)
    setIsLoading(false)
  }

  async function signOut() {
    sessionStorage.removeItem('active_tenant_id')
    sessionStorage.removeItem('auth_return_url')
    await supabase.auth.signOut()
  }

  const isAdminAccount = profile?.system_role === 'SUPER_ADMIN' || profile?.system_role === 'ADMIN_USER'

  return (
    <AuthContext.Provider value={{
      session,
      user: session?.user ?? null,
      profile,
      isLoading,
      isAdminAccount,
      signOut,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
