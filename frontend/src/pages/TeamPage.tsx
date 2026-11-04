import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { TeamUser } from '../api/types'
import { useApi } from '../api/useApi'
import { useAuth } from '../auth/AuthContext'
import { Avatar, Badge, Button, Card, ErrorNote, Field, Modal, Spinner, formatDate, inputClass } from '../components/ui'

export default function TeamPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'TENANT_ADMIN'
  const team = useApi(() => api.list<TeamUser>('/api/v1/users?size=100'), [])
  const [adding, setAdding] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const deactivate = async (member: TeamUser) => {
    if (!window.confirm(`Deactivate ${member.firstName} ${member.lastName}? They will be signed out and unable to log in.`)) return
    setActionError(null)
    try {
      await api.patch(`/api/v1/users/${member.id}/deactivate`)
      team.reload()
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Team</h1>
          <p className="text-sm text-slate-500">People in your workspace.</p>
        </div>
        {isAdmin && <Button onClick={() => setAdding(true)}>Add member</Button>}
      </header>
      {actionError && <ErrorNote message={actionError} />}
      {team.loading && !team.data ? (
        <Spinner />
      ) : team.error ? (
        <ErrorNote message={team.error} onRetry={team.reload} />
      ) : (
        <Card className="p-0">
          <ul className="divide-y divide-slate-100">
            {team.data?.data.map((member) => {
              const name = `${member.firstName} ${member.lastName}`
              return (
                <li key={member.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <Avatar name={name} />
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {name} {member.id === user?.userId && <span className="text-xs text-slate-400">(you)</span>}
                      </p>
                      <p className="truncate text-sm text-slate-500">{member.email}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={member.role === 'TENANT_ADMIN' ? 'indigo' : 'slate'}>{member.role === 'TENANT_ADMIN' ? 'Admin' : 'Member'}</Badge>
                    <Badge tone={member.status === 'ACTIVE' ? 'green' : 'red'}>{member.status === 'ACTIVE' ? 'Active' : 'Deactivated'}</Badge>
                    <span className="hidden text-xs text-slate-400 sm:inline">Joined {formatDate(member.createdAt)}</span>
                    {isAdmin && member.status === 'ACTIVE' && member.id !== user?.userId && (
                      <Button variant="danger" onClick={() => deactivate(member)}>
                        Deactivate
                      </Button>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        </Card>
      )}
      {adding && (
        <AddMemberModal
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false)
            team.reload()
          }}
        />
      )}
    </div>
  )
}

function AddMemberModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.post('/api/v1/users', { ...form, email: form.email.trim() })
      onSaved()
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <Modal title="Add team member" onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <Field label="First name">
            <input className={inputClass} required maxLength={100} autoFocus value={form.firstName} onChange={set('firstName')} />
          </Field>
          <Field label="Last name">
            <input className={inputClass} required maxLength={100} value={form.lastName} onChange={set('lastName')} />
          </Field>
        </div>
        <Field label="Email">
          <input className={inputClass} type="email" required value={form.email} onChange={set('email')} />
        </Field>
        <Field label="Temporary password (12+ characters)">
          <input className={inputClass} type="password" required minLength={12} maxLength={72} autoComplete="new-password" value={form.password} onChange={set('password')} />
        </Field>
        {error && <ErrorNote message={error} />}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? 'Adding...' : 'Add member'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}