import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Avatar, Badge, Button } from './ui'

const links = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/projects', label: 'Projects', end: false },
  { to: '/tasks', label: 'Task board', end: false },
  { to: '/team', label: 'Team', end: false },
]

export default function Layout() {
  const { user, logout } = useAuth()
  if (!user) return null
  const fullName = `${user.firstName} ${user.lastName}`

  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-slate-200 bg-white md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between px-5 py-4 md:block">
          <div className="flex items-center gap-2 text-lg font-semibold text-indigo-700">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-sm text-white">W</span>
            WorkSphere
          </div>
          <div className="md:hidden">
            <Button variant="ghost" onClick={logout}>
              Sign out
            </Button>
          </div>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium ${
                  isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100'
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto hidden border-t border-slate-200 p-4 md:absolute md:bottom-0 md:block md:w-60">
          <div className="flex items-center gap-3">
            <Avatar name={fullName} />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{fullName}</p>
              <Badge tone={user.role === 'TENANT_ADMIN' ? 'indigo' : 'slate'}>
                {user.role === 'TENANT_ADMIN' ? 'Admin' : 'Member'}
              </Badge>
            </div>
          </div>
          <Button variant="secondary" className="mt-3 w-full" onClick={logout}>
            Sign out
          </Button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 p-4 md:p-8">
        <Outlet />
      </main>
    </div>
  )
}