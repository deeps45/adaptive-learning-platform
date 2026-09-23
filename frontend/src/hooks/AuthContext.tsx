import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { decodeAccessToken, login as apiLogin, logout as apiLogout, register as apiRegister } from '../api/auth'
import { getAccessToken } from '../api/client'
import type { Role } from '../types'

interface AuthUser {
  id: string
  email: string
  role: Role
}

interface AuthContextValue {
  user: AuthUser | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, fullName: string, role: Role) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readUserFromStoredToken(): AuthUser | null {
  const token = getAccessToken()
  if (!token) return null
  const decoded = decodeAccessToken(token)
  return decoded ? { id: decoded.id, email: decoded.email, role: decoded.role } : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readUserFromStoredToken())

  const login = useCallback(async (email: string, password: string) => {
    await apiLogin(email, password)
    setUser(readUserFromStoredToken())
  }, [])

  const register = useCallback(async (email: string, password: string, fullName: string, role: Role) => {
    await apiRegister(email, password, fullName, role)
    await apiLogin(email, password)
    setUser(readUserFromStoredToken())
  }, [])

  const logout = useCallback(() => {
    apiLogout()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, login, register, logout }), [user, login, register, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
