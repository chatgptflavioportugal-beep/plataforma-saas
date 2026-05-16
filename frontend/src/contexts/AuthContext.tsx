import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from '@/lib/supabase'
import { queryClient } from '@/lib/query-client'
import type { UserProfile } from '@/types'

interface AuthContextValue {
  session: Session | null
  user: User | null
  profile: UserProfile | null
  isLoading: boolean
  isSuperAdmin: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    // onAuthStateChange dispara INITIAL_SESSION imediatamente com a sessão
    // atual, cobrindo tanto a hidratação inicial quanto sign-in/sign-out.
    // Usar getSession() em paralelo causava dupla chamada a loadProfile().
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

  return (
    <AuthContext.Provider value={{
      session,
      user: session?.user ?? null,
      profile,
      isLoading,
      isSuperAdmin: profile?.system_role === 'SUPER_ADMIN',
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
