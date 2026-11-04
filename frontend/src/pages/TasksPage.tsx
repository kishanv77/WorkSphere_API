import { useState } from 'react'
import type { DragEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, errorMessage } from '../api/client'
import { canModify } from '../api/permissions'
import type { Project, Task, TaskStatus, TeamUser } from '../api/types'
import { useApi } from '../api/useApi'
import { useAuth } from '../auth/AuthContext'
import { Avatar, Badge, Button, EmptyState, ErrorNote, Spinner, formatDate, inputClass, isOverdue } from '../components/ui'
import TaskCreateModal from './TaskCreateModal'
import TaskDrawer from './TaskDrawer'

const COLUMNS: { key: TaskStatus; label: string; accent: string }[] = [
  { key: 'TODO', label: 'To do', accent: 'bg-slate-400' },
  { key: 'IN_PROGRESS', label: 'In progress', accent: 'bg-indigo-500' },
  { key: 'DONE', label: 'Done', accent: 'bg-emerald-500' },
]

export default function TasksPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const projectFilter = params.get('project') ?? ''
  const assigneeFilter = params.get('assignee') ?? ''
  const [creating, setCreating] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState<TaskStatus | null>(null)

  const projects = useApi(() => api.list<Project>('/api/v1/projects?size=100').then((r) => r.data), [])
  const team = useApi(() => api.list<TeamUser>('/api/v1/users?size=100').then((r) => r.data), [])
  const tasks = useApi(
    () => {
      const query = new URLSearchParams({ size: '100' })
      if (projectFilter) query.set('projectId', projectFilter)
      if (assigneeFilter) query.set('assigneeId', assigneeFilter === 'me' ? (user?.userId ?? '') : assigneeFilter)
      return api.list<Task>(`/api/v1/tasks?${query.toString()}`)
    },
    [projectFilter, assigneeFilter, user?.userId],
  )

  if (!user) return null
  const list = tasks.data?.data ?? []
  const people = team.data ?? []
  const activeProjects = (projects.data ?? []).filter((p) => p.status === 'ACTIVE')
  const nameOf = (id: string | null) => {
    const person = people.find((p) => p.id === id)
    return person ? `${person.firstName} ${person.lastName}` : null
  }
  const projectName = (id: string) => projects.data?.find((p) => p.id === id)?.name ?? 'Project'

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const replaceTask = (updated: Task) =>
    tasks.setData((current) =>
      current ? { ...current, data: current.data.map((t) => (t.id === updated.id ? updated : t)) } : current,
    )

  const moveTask = async (taskId: string, status: TaskStatus) => {
    const task = list.find((t) => t.id === taskId)
    if (!task || task.status === status) return
    if (!canModify(task, user)) {
      setNotice('You can only move tasks you created or are assigned to.')
      return
    }
    setNotice(null)
    replaceTask({ ...task, status })
    try {
      replaceTask(await api.patch<Task>(`/api/v1/tasks/${taskId}/status`, { status }))
    } catch (e) {
      replaceTask(task)
      setNotice(errorMessage(e))
    }
  }

  const onDrop = (event: DragEvent, status: TaskStatus) => {
    event.preventDefault()
    setDragOver(null)
    void moveTask(event.dataTransfer.getData('text/plain'), status)
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Task board</h1>
          <p className="text-sm text-slate-500">Drag cards between columns, or open a task for details and comments.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select aria-label="Filter by project" className={`${inputClass} w-44`} value={projectFilter} onChange={(e) => setFilter('project', e.target.value)}>
            <option value="">All projects</option>
            {(projects.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
                {p.status === 'ARCHIVED' ? ' (archived)' : ''}
              </option>
            ))}
          </select>
          <select aria-label="Filter by assignee" className={`${inputClass} w-40`} value={assigneeFilter} onChange={(e) => setFilter('assignee', e.target.value)}>
            <option value="">Everyone</option>
            <option value="me">Assigned to me</option>
            {people.filter((p) => p.id !== user.userId).map((p) => (
              <option key={p.id} value={p.id}>
                {p.firstName} {p.lastName}
              </option>
            ))}
          </select>
          <Button onClick={() => setCreating(true)} disabled={activeProjects.length === 0}>
            New task
          </Button>
        </div>
      </header>

      {notice && (
        <div role="alert" className="flex items-center justify-between rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
          {notice}
          <button onClick={() => setNotice(null)} className="font-medium underline">
            Dismiss
          </button>
        </div>
      )}
      {tasks.error && <ErrorNote message={tasks.error} onRetry={tasks.reload} />}
      {tasks.loading && !tasks.data ? (
        <Spinner />
      ) : activeProjects.length === 0 && !projects.loading ? (
        <EmptyState title="No active projects" hint={user.role === 'TENANT_ADMIN' ? 'Create a project first, then add tasks to it.' : 'Ask an admin to create a project.'} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            {COLUMNS.map((column) => {
              const items = list.filter((t) => t.status === column.key)
              return (
                <section
                  key={column.key}
                  aria-label={column.label}
                  onDragOver={(e) => {
                    e.preventDefault()
                    setDragOver(column.key)
                  }}
                  onDragLeave={() => setDragOver((current) => (current === column.key ? null : current))}
                  onDrop={(e) => onDrop(e, column.key)}
                  className={`min-h-48 rounded-xl p-3 ring-1 transition-colors ${dragOver === column.key ? 'bg-indigo-50 ring-indigo-300' : 'bg-slate-100/70 ring-slate-200'}`}
                >
                  <h2 className="mb-3 flex items-center gap-2 px-1 text-sm font-semibold text-slate-700">
                    <span className={`h-2.5 w-2.5 rounded-full ${column.accent}`} />
                    {column.label}
                    <span className="rounded-full bg-white px-2 text-xs font-medium text-slate-500 ring-1 ring-slate-200">{items.length}</span>
                  </h2>
                  <ul className="space-y-2">
                    {items.map((task) => {
                      const assignee = nameOf(task.assigneeId)
                      const movable = canModify(task, user)
                      return (
                        <li key={task.id}>
                          <button
                            draggable={movable}
                            onDragStart={(e) => {
                              e.dataTransfer.setData('text/plain', task.id)
                              e.dataTransfer.effectAllowed = 'move'
                            }}
                            onClick={() => setOpenId(task.id)}
                            className={`w-full rounded-lg bg-white p-3 text-left shadow-sm ring-1 ring-slate-200 hover:ring-indigo-300 ${movable ? 'cursor-grab active:cursor-grabbing' : ''}`}
                          >
                            <p className="font-medium text-slate-900">{task.title}</p>
                            <p className="mt-0.5 truncate text-xs text-slate-500">{projectName(task.projectId)}</p>
                            <div className="mt-2 flex flex-wrap items-center gap-1.5">
                              <Badge tone={task.priority === 'HIGH' ? 'red' : task.priority === 'MEDIUM' ? 'amber' : 'slate'}>{task.priority}</Badge>
                              {task.dueDate && <Badge tone={isOverdue(task.dueDate, task.status) ? 'red' : 'slate'}>{formatDate(task.dueDate)}</Badge>}
                              {assignee && (
                                <span className="ml-auto">
                                  <Avatar name={assignee} />
                                </span>
                              )}
                            </div>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                  {items.length === 0 && <p className="px-1 py-6 text-center text-xs text-slate-400">Drop tasks here</p>}
                </section>
              )
            })}
          </div>
          {tasks.data?.total != null && tasks.data.total > list.length && (
            <p className="text-center text-xs text-slate-500">Showing the newest {list.length} of {tasks.data.total} tasks. Use the filters to narrow down.</p>
          )}
        </>
      )}

      {creating && (
        <TaskCreateModal
          projects={activeProjects}
          people={people.filter((p) => p.status === 'ACTIVE')}
          defaultProjectId={projectFilter || activeProjects[0]?.id}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            tasks.reload()
          }}
        />
      )}
      {openId && (
        <TaskDrawer
          task={list.find((t) => t.id === openId) ?? null}
          projects={projects.data ?? []}
          people={people}
          user={user}
          onClose={() => setOpenId(null)}
          onChanged={(updated) => replaceTask(updated)}
        />
      )}
    </div>
  )
}