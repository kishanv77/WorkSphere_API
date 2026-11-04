import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import { canModify } from '../api/permissions'
import type { Me, Priority, Project, Task, TaskComment, TaskStatus, TeamUser } from '../api/types'
import { useApi } from '../api/useApi'
import { Avatar, Button, ErrorNote, Field, Spinner, formatDate, inputClass } from '../components/ui'


interface Props {
  task: Task | null
  projects: Project[]
  people: TeamUser[]
  user: Me
  onClose: () => void
  onChanged: (task: Task) => void
}

export default function TaskDrawer({ task, projects, people, user, onClose, onChanged }: Props) {
  const [title, setTitle] = useState(task?.title ?? '')
  const [description, setDescription] = useState(task?.description ?? '')
  const [priority, setPriority] = useState<Priority>(task?.priority ?? 'MEDIUM')
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '')
  const [assigneeId, setAssigneeId] = useState(task?.assigneeId ?? '')
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [comment, setComment] = useState('')
  const [commenting, setCommenting] = useState(false)
  const taskId = task?.id

  const comments = useApi(
    () => (taskId ? api.list<TaskComment>(`/api/v1/tasks/${taskId}/comments?size=100`).then((r) => r.data) : Promise.resolve([])),
    [taskId],
  )

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  if (!task) return null
  const editable = canModify(task, user)
  const projectName = projects.find((p) => p.id === task.projectId)?.name ?? 'Project'
  const assignable = people.filter((p) => p.status === 'ACTIVE' || p.id === task.assigneeId)

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      if (status !== task.status) {
        onChanged(await api.patch<Task>(`/api/v1/tasks/${task.id}/status`, { status }))
      }
      const body: Record<string, unknown> = { title, description, priority }
      if (dueDate) body.dueDate = dueDate
      else if (task.dueDate) body.clearDueDate = true
      if (assigneeId) body.assigneeId = assigneeId
      else if (task.assigneeId) body.unassign = true
      onChanged(await api.patch<Task>(`/api/v1/tasks/${task.id}`, body))
      setSaved(true)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const addComment = async (event: FormEvent) => {
    event.preventDefault()
    setCommenting(true)
    try {
      await api.post(`/api/v1/tasks/${task.id}/comments`, { body: comment })
      setComment('')
      comments.reload()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setCommenting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-slate-900/30" onMouseDown={onClose}>
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Task details"
        className="flex h-full w-full max-w-xl flex-col overflow-y-auto bg-white shadow-2xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b border-slate-200 px-6 py-4">
          <div className="min-w-0">
            <p className="text-xs text-slate-500">{projectName}</p>
            <h2 className="truncate text-lg font-semibold">{task.title}</h2>
          </div>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
            ✕
          </button>
        </div>

        <form onSubmit={save} className="space-y-4 px-6 py-5">
          {!editable && (
            <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600 ring-1 ring-slate-200">
              You can read this task and comment on it. Only its creator, its assignee or an admin can edit it.
            </p>
          )}
          <Field label="Title">
            <input className={inputClass} required maxLength={200} disabled={!editable} value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Description">
            <textarea className={inputClass} rows={3} disabled={!editable} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Status">
              <select className={inputClass} disabled={!editable} value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)}>
                <option value="TODO">To do</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="DONE">Done</option>
              </select>
            </Field>
            <Field label="Priority">
              <select className={inputClass} disabled={!editable} value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </select>
            </Field>
            <Field label="Due date">
              <input className={inputClass} type="date" disabled={!editable} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </Field>
            <Field label="Assignee">
              <select className={inputClass} disabled={!editable} value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
                <option value="">Unassigned</option>
                {assignable.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.firstName} {p.lastName}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          {error && <ErrorNote message={error} />}
          {editable && (
            <div className="flex items-center justify-end gap-3">
              {saved && <span className="text-sm text-emerald-600">Saved</span>}
              <Button type="submit" disabled={busy || title.trim() === ''}>
                {busy ? 'Saving...' : 'Save changes'}
              </Button>
            </div>
          )}
        </form>

        <section className="flex-1 border-t border-slate-200 px-6 py-5" aria-label="Comments">
          <h3 className="mb-3 font-semibold">Comments</h3>
          {comments.loading && !comments.data ? (
            <Spinner />
          ) : comments.error ? (
            <ErrorNote message={comments.error} onRetry={comments.reload} />
          ) : comments.data && comments.data.length === 0 ? (
            <p className="text-sm text-slate-500">No comments yet. Start the conversation.</p>
          ) : (
            <ul className="space-y-4">
              {comments.data?.map((c) => (
                <li key={c.id} className="flex gap-3">
                  <Avatar name={c.authorName} />
                  <div className="min-w-0">
                    <p className="text-sm">
                      <span className="font-medium">{c.authorName}</span> <span className="text-xs text-slate-400">{formatDate(c.createdAt)}</span>
                    </p>
                    <p className="whitespace-pre-wrap break-words text-sm text-slate-700">{c.body}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <form onSubmit={addComment} className="mt-5 space-y-2">
            <textarea
              className={inputClass}
              rows={2}
              maxLength={2000}
              placeholder="Write a comment..."
              aria-label="New comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
            <div className="flex justify-end">
              <Button type="submit" disabled={commenting || comment.trim() === ''}>
                {commenting ? 'Posting...' : 'Comment'}
              </Button>
            </div>
          </form>
        </section>
      </aside>
    </div>
  )
}