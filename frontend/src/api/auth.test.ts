import { describe, expect, it } from 'vitest'
import { decodeAccessToken } from './auth'

function fakeJwt(payload: object): string {
  const base64url = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${base64url({ alg: 'HS384' })}.${base64url(payload)}.fakesignature`
}

describe('decodeAccessToken', () => {
  it('extracts id, email, and role from a well-formed token', () => {
    const token = fakeJwt({ sub: 'user-123', email: 'a@example.com', role: 'STUDENT' })
    expect(decodeAccessToken(token)).toEqual({ id: 'user-123', email: 'a@example.com', role: 'STUDENT' })
  })

  it('returns null for a malformed token instead of throwing', () => {
    expect(decodeAccessToken('not-a-jwt')).toBeNull()
    expect(decodeAccessToken('')).toBeNull()
    expect(decodeAccessToken('a.b')).toBeNull()
  })
})
