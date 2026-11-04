import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { Priority, Project, TeamUser } from '../api/types'
import { Button, ErrorNote, Field, Modal, inputClass } from '../components/ui'

interface Props {
  projects: Project[]
  people: TeamUser[]
  defaultProjectId?: string
  onClose: () => void
  onCreated: () => void
}

export default function TaskCreateModal({ projects, people, defaultProjectId, onClose, onCreated }: Props) {
  const [projectId, setProjectId] = useState(defaultProjectId ?? projects[0]?.id ?? '')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [dueDate, setDueDate] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.post('/api/v1/tasks', {
        projectId,
        title,
        description: description || undefined,
        priority,
        dueDate: dueDate || undefined,
        assigneeId: assigneeId || undefined,
      })
      onCreated()
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <Modal title="New task" onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Title">
          <input className={inputClass} required maxLength={200} autoFocus value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <Field label="Project">
          <select className={inputClass} required value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Description (optional)">
          <textarea className={inputClass} rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Priority">
            <select className={inputClass} value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
          </Field>
          <Field label="Due date">
            <input className={inputClass} type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </Field>
        </div>
        <Field label="Assignee">
          <select className={inputClass} value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
            <option value="">Unassigned</option>
            {people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.firstName} {p.lastName}
              </option>
            ))}
          </select>
        </Field>
        {error && <ErrorNote message={error} />}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy || title.trim() === ''}>
            {busy ? 'Creating...' : 'Create task'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}