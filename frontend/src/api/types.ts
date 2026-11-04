export type Role = 'TENANT_ADMIN' | 'MEMBER'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Me {
  userId: string
  tenantId: string
  email: string
  firstName: string
  lastName: string
  role: Role
}

export interface Project {
  id: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
}

export interface Task {
  id: string
  projectId: string
  assigneeId: string | null
  createdById: string | null
  title: string
  description: string | null
  status: TaskStatus
  priority: Priority
  dueDate: string | null
  createdAt: string
  updatedAt: string
}

export interface TeamUser {
  id: string
  email: string
  firstName: string
  lastName: string
  role: Role
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
}

export interface TaskComment {
  id: string
  taskId: string
  authorId: string
  authorName: string
  body: string
  createdAt: string
}

export interface Dashboard {
  tasksByStatus: Record<TaskStatus, number>
  overdueTasks: number
  myOpenTasks: number
  activeProjects: number
  archivedProjects: number
  activeMembers: number
}