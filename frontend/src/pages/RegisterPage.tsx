import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'
import type { Role } from '../types'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('STUDENT')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await register(email, password, fullName, role)
      navigate('/')
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string } } })?.response?.data?.error ??
        'Could not create account'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto mt-16 max-w-sm">
      <h1 className="mb-6 text-2xl font-semibold text-slate-900">Create an account</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="register-fullname" className="mb-1 block text-sm font-medium text-slate-700">
            Full name
          </label>
          <input
            id="register-fullname"
            required
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </div>
        <div>
          <label htmlFor="register-email" className="mb-1 block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="register-email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </div>
        <div>
          <label htmlFor="register-password" className="mb-1 block text-sm font-medium text-slate-700">
            Password
          </label>
          <input
            id="register-password"
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">I am a…</label>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" checked={role === 'STUDENT'} onChange={() => setRole('STUDENT')} />
              Student
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" checked={role === 'INSTRUCTOR'} onChange={() => setRole('INSTRUCTOR')} />
              Instructor
            </label>
          </div>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {submitting ? 'Creating…' : 'Sign up'}
        </button>
      </form>
    </div>
  )
}
