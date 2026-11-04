import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Button, ErrorNote, Field, inputClass } from '../components/ui'

export default function LoginPage() {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fields, setFields] = useState<Record<string, string>>({})
  const [form, setForm] = useState({
    tenantName: '',
    tenantSlug: '',
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  })

  if (user) return <Navigate to="/" replace />

  const set = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFields({})
    try {
      if (mode === 'login') await login(form.email.trim(), form.password)
      else await register({ ...form, email: form.email.trim() })
      navigate('/', { replace: true })
    } catch (e) {
      if (e instanceof ApiError) setFields(e.fieldErrors)
      setError(e instanceof ApiError && e.status === 401 ? 'Incorrect email or password.' : errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const slugify = (value: string) =>
    value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80)

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-slate-100 p-4">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <span className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-600 text-xl font-bold text-white">W</span>
          <h1 className="mt-3 text-2xl font-semibold">WorkSphere</h1>
          <p className="text-sm text-slate-500">Projects and tasks for your whole team</p>
        </div>
        <div className="rounded-2xl bg-white p-6 shadow-lg ring-1 ring-slate-200">
          <div className="mb-5 grid grid-cols-2 rounded-lg bg-slate-100 p-1 text-sm font-medium" role="tablist">
            {(['login', 'register'] as const).map((tab) => (
              <button
                key={tab}
                role="tab"
                aria-selected={mode === tab}
                onClick={() => {
                  setMode(tab)
                  setError(null)
                  setFields({})
                }}
                className={`rounded-md py-1.5 ${mode === tab ? 'bg-white shadow-sm' : 'text-slate-500'}`}
              >
                {tab === 'login' ? 'Sign in' : 'Create workspace'}
              </button>
            ))}
          </div>
          <form onSubmit={submit} className="space-y-4">
            {mode === 'register' && (
              <>
                <Field label="Workspace name" error={fields.tenantName}>
                  <input
                    className={inputClass}
                    required
                    maxLength={150}
                    value={form.tenantName}
                    onChange={(event) => {
                      set('tenantName')(event)
                      setForm((current) => ({ ...current, tenantSlug: slugify(event.target.value) }))
                    }}
                  />
                </Field>
                <Field label="Workspace URL name" error={fields.tenantSlug}>
                  <input className={inputClass} required maxLength={80} value={form.tenantSlug} onChange={set('tenantSlug')} />
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="First name" error={fields.firstName}>
                    <input className={inputClass} required maxLength={100} value={form.firstName} onChange={set('firstName')} />
                  </Field>
                  <Field label="Last name" error={fields.lastName}>
                    <input className={inputClass} required maxLength={100} value={form.lastName} onChange={set('lastName')} />
                  </Field>
                </div>
              </>
            )}
            <Field label="Email" error={fields.email}>
              <input className={inputClass} type="email" required autoComplete="email" value={form.email} onChange={set('email')} />
            </Field>
            <Field label={mode === 'register' ? 'Password (12+ characters)' : 'Password'} error={fields.password}>
              <input
                className={inputClass}
                type="password"
                required
                minLength={mode === 'register' ? 12 : undefined}
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                value={form.password}
                onChange={set('password')}
              />
            </Field>
            {error && <ErrorNote message={error} />}
            <Button type="submit" className="w-full" disabled={busy}>
              {busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create workspace'}
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}