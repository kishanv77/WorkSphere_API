import type { Me, Task } from './types'

/** Mirrors the API rule: admins can change any task, members only tasks they created or are assigned to. */
export function canModify(task: Task, user: Me): boolean {
  return user.role === 'TENANT_ADMIN' || task.assigneeId === user.userId || task.createdById === user.userId
}