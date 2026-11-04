import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Dashboard, Project, Task } from '../api/types'
import { useApi } from '../api/useApi'
import { useAuth } from '../auth/AuthContext'
import { Badge, Card, ErrorNote, EmptyState, Spinner, formatDate, isOverdue } from '../components/ui'

function Stat({ label, value, tone = 'text-slate-900', to }: { label: string; value: number; tone?: string; to?: string }) {
  const body = (
    <Card className="h-full transition-shadow hover:shadow-md">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold ${tone}`}>{value}</p>
    </Card>
  )
  return to ? <Link to={to}>{body}</Link> : body
}

export default function DashboardPage() {
  const { user } = useAuth()
  const summary = useApi(() => api.get<Dashboard>('/api/v1/dashboard'), [])
  const mine = useApi(
    async () => {
      const tasks = await api.list<Task>(`/api/v1/tasks?assigneeId=${user?.userId}&size=100`)
      const projects = await api.list<Project>('/api/v1/projects?size=100')
      return { tasks: tasks.data.filter((t) => t.status !== 'DONE'), projects: projects.data }
    },
    [user?.userId],
  )

  if (summary.loading && !summary.data) return <Spinner />
  if (summary.error || !summary.data) return <ErrorNote message={summary.error ?? 'No data'} onRetry={summary.reload} />

  const { tasksByStatus, overdueTasks, myOpenTasks, activeProjects, activeMembers } = summary.data
  const total = tasksByStatus.TODO + tasksByStatus.IN_PROGRESS + tasksByStatus.DONE
  const segments = [
    { key: 'To do', value: tasksByStatus.TODO, color: 'bg-slate-400' },
    { key: 'In progress', value: tasksByStatus.IN_PROGRESS, color: 'bg-indigo-500' },
    { key: 'Done', value: tasksByStatus.DONE, color: 'bg-emerald-500' },
  ]
  const projectName = (id: string) => mine.data?.projects.find((p) => p.id === id)?.name ?? 'Project'

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Welcome back, {user?.firstName}</h1>
        <p className="text-sm text-slate-500">Here is what is happening in your workspace.</p>
      </header>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="My open tasks" value={myOpenTasks} tone="text-indigo-700" to="/tasks" />
        <Stat label="Overdue" value={overdueTasks} tone={overdueTasks > 0 ? 'text-red-600' : 'text-slate-900'} />
        <Stat label="Active projects" value={activeProjects} to="/projects" />
        <Stat label="Team members" value={activeMembers} to="/team" />
      </div>

      <Card>
        <h2 className="font-semibold">Task progress</h2>
        {total === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No tasks yet. Create one from the task board.</p>
        ) : (
          <>
            <div className="mt-3 flex h-3 overflow-hidden rounded-full bg-slate-100" role="img" aria-label={segments.map((s) => `${s.key}: ${s.value}`).join(', ')}>
              {segments.map((s) => (
                <div key={s.key} className={s.color} style={{ width: `${(s.value / total) * 100}%` }} />
              ))}
            </div>
            <ul className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
              {segments.map((s) => (
                <li key={s.key} className="flex items-center gap-2">
                  <span className={`h-2.5 w-2.5 rounded-full ${s.color}`} />
                  {s.key}: <strong className="text-slate-900">{s.value}</strong>
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold">Assigned to me</h2>
          <Link to="/tasks" className="text-sm font-medium text-indigo-600 hover:underline">
            Open board
          </Link>
        </div>
        {mine.loading && !mine.data ? (
          <Spinner />
        ) : mine.error ? (
          <ErrorNote message={mine.error} onRetry={mine.reload} />
        ) : mine.data && mine.data.tasks.length === 0 ? (
          <EmptyState title="Nothing assigned to you" hint="Tasks assigned to you will show up here." />
        ) : (
          <ul className="divide-y divide-slate-100">
            {mine.data?.tasks.map((task) => (
              <li key={task.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                <div className="min-w-0">
                  <p className="truncate font-medium">{task.title}</p>
                  <p className="text-xs text-slate-500">{projectName(task.projectId)}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={task.priority === 'HIGH' ? 'red' : task.priority === 'MEDIUM' ? 'amber' : 'slate'}>{task.priority}</Badge>
                  <Badge tone={isOverdue(task.dueDate, task.status) ? 'red' : 'slate'}>{task.dueDate ? formatDate(task.dueDate) : 'No due date'}</Badge>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}