import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, tokenStore } from '../api/client'
import type { Me } from '../api/types'

interface RegisterInput {
  tenantName: string
  tenantSlug: string
  firstName: string
  lastName: string
  email: string
  password: string
}

interface AuthState {
  user: Me | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState<boolean>(tokenStore.get() !== null)

  useEffect(() => {
    if (tokenStore.get() === null) return
    api
      .get<Me>('/api/v1/auth/me')
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const expired = () => setUser(null)
    window.addEventListener('auth:expired', expired)
    return () => window.removeEventListener('auth:expired', expired)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const result = await api.post<{ accessToken: string }>('/api/v1/auth/login', { email, password })
    tokenStore.set(result.accessToken)
    setUser(await api.get<Me>('/api/v1/auth/me'))
  }, [])

  const register = useCallback(
    async (input: RegisterInput) => {
      await api.post('/api/v1/auth/register-tenant', input)
      await login(input.email, input.password)
    },
    [login],
  )

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}