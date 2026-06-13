import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { Session, User } from '@supabase/supabase-js'
import { supabase } from './supabase'
import { queryClient } from '@/shared/services/query-client'
import { api } from '@/shared/services/api'
import type { UserProfile } from '@/shared/types'

interface AuthContextValue {
  session: Session | null
  user: User | null
  profile: UserProfile | null
  isLoading: boolean
  isSuperAdmin: boolean
  isAdminUser: boolean
  adminPermissions: Set<string>
  hasAdminPermission: (key: string) => boolean
  refreshAdminPermissions: () => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [adminPermissions, setAdminPermissions] = useState<Set<string>>(new Set())
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session)
      if (session?.user) {
        loadProfile(session.user.id)
      } else {
        setProfile(null)
        setAdminPermissions(new Set())
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

    // Carregar permissões se for ADMIN_USER
    if (data?.system_role === 'ADMIN_USER' && data?.admin_access_level_id) {
      await loadAdminPermissions()
    } else {
      setAdminPermissions(new Set())
    }

    setIsLoading(false)
  }

  async function loadAdminPermissions() {
    try {
      const { data } = await api.get<{ permissionKeys: string[] }>(
        '/api/v1/admin/access-levels/my-permissions'
      )
      setAdminPermissions(new Set(data.permissionKeys ?? []))
    } catch {
      setAdminPermissions(new Set())
    }
  }

  async function signOut() {
    sessionStorage.removeItem('active_tenant_id')
    sessionStorage.removeItem('auth_return_url')
    await supabase.auth.signOut()
  }

  const isSuperAdmin = profile?.system_role === 'SUPER_ADMIN'
  const isAdminUser = profile?.system_role === 'ADMIN_USER' && (profile?.is_active ?? false)

  function hasAdminPermission(key: string): boolean {
    if (isSuperAdmin) return true
    if (!isAdminUser) return false
    return adminPermissions.has(key)
  }

  return (
    <AuthContext.Provider value={{
      session,
      user: session?.user ?? null,
      profile,
      isLoading,
      isSuperAdmin,
      isAdminUser,
      adminPermissions,
      hasAdminPermission,
      refreshAdminPermissions: loadAdminPermissions,
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
