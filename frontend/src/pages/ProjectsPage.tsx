import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import type { Project } from '../api/types'
import { useApi } from '../api/useApi'
import { useAuth } from '../auth/AuthContext'
import { Badge, Button, Card, EmptyState, ErrorNote, Field, Modal, Spinner, formatDate, inputClass } from '../components/ui'

export default function ProjectsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'TENANT_ADMIN'
  const projects = useApi(() => api.list<Project>('/api/v1/projects?size=100'), [])
  const [editing, setEditing] = useState<Project | 'new' | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const archive = async (project: Project) => {
    if (!window.confirm(`Archive "${project.name}"? Archived projects cannot receive new tasks.`)) return
    setActionError(null)
    try {
      await api.patch(`/api/v1/projects/${project.id}/archive`)
      projects.reload()
    } catch (e) {
      setActionError(errorMessage(e))
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Projects</h1>
          <p className="text-sm text-slate-500">{isAdmin ? 'Create and manage your workspace projects.' : 'Projects in your workspace.'}</p>
        </div>
        {isAdmin && <Button onClick={() => setEditing('new')}>New project</Button>}
      </header>

      {actionError && <ErrorNote message={actionError} />}
      {projects.loading && !projects.data ? (
        <Spinner />
      ) : projects.error ? (
        <ErrorNote message={projects.error} onRetry={projects.reload} />
      ) : projects.data && projects.data.data.length === 0 ? (
        <EmptyState
          title="No projects yet"
          hint={isAdmin ? 'Create your first project to start adding tasks.' : 'An admin has not created any projects yet.'}
          action={isAdmin ? <Button onClick={() => setEditing('new')}>New project</Button> : undefined}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {projects.data?.data.map((project) => (
            <Card key={project.id} className="flex flex-col">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-semibold">{project.name}</h2>
                <Badge tone={project.status === 'ACTIVE' ? 'green' : 'slate'}>{project.status === 'ACTIVE' ? 'Active' : 'Archived'}</Badge>
              </div>
              <p className="mt-1 flex-1 text-sm text-slate-600">{project.description ?? 'No description'}</p>
              <p className="mt-3 text-xs text-slate-400">Created {formatDate(project.createdAt)}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link to={`/tasks?project=${project.id}`}>
                  <Button variant="secondary">Open board</Button>
                </Link>
                {isAdmin && (
                  <>
                    <Button variant="ghost" onClick={() => setEditing(project)}>
                      Edit
                    </Button>
                    {project.status === 'ACTIVE' && (
                      <Button variant="danger" onClick={() => archive(project)}>
                        Archive
                      </Button>
                    )}
                  </>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {editing && (
        <ProjectModal
          project={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            projects.reload()
          }}
        />
      )}
    </div>
  )
}

function ProjectModal({ project, onClose, onSaved }: { project: Project | null; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (project) await api.patch(`/api/v1/projects/${project.id}`, { name, description })
      else await api.post('/api/v1/projects', { name, description })
      onSaved()
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <Modal title={project ? 'Edit project' : 'New project'} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Name">
          <input className={inputClass} required maxLength={150} autoFocus value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Description (optional)">
          <textarea className={inputClass} rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        {error && <ErrorNote message={error} />}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy || name.trim() === ''}>
            {busy ? 'Saving...' : 'Save'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}