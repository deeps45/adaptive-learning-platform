import { api, clearTokens, setTokens } from './client'
import type { Role, User } from '../types'

export async function register(email: string, password: string, fullName: string, role: Role): Promise<User> {
  const res = await api.post<User>('/auth/register', { email, password, fullName, role })
  return res.data
}

export async function login(email: string, password: string): Promise<void> {
  const res = await api.post<{ accessToken: string; refreshToken: string }>('/auth/login', {
    email,
    password,
  })
  setTokens(res.data.accessToken, res.data.refreshToken)
}

export function logout(): void {
  clearTokens()
}

/** Decodes the JWT payload client-side purely for display (name/role/id) - the server is the
 * only thing that ever verifies the signature; nothing here is trusted for authorization. */
export function decodeAccessToken(token: string): { id: string; email: string; role: Role } | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const parsed = JSON.parse(json)
    return { id: parsed.sub, email: parsed.email, role: parsed.role }
  } catch {
    return null
  }
}
