const BASE: string = import.meta.env.VITE_API_URL ?? 'http://127.0.0.1:8080'
const TOKEN_KEY = 'worksphere.token'

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* storage unavailable: the session just won't survive a reload */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const details = Object.values(error.fieldErrors)
    return details.length > 0 ? details.join('. ') : error.message
  }
  return 'Something went wrong. Please try again.'
}

interface Result<T> {
  data: T
  total: number | null
}

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<Result<T>> {
  const headers: Record<string, string> = {}
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the API running on ' + BASE + '?')
  }

  const text = await response.text()
  let json: { message?: string; fieldErrors?: Record<string, string> } | null = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
  }

  if (!response.ok) {
    if (response.status === 401 && token) {
      tokenStore.clear()
      window.dispatchEvent(new Event('auth:expired'))
    }
    throw new ApiError(response.status, json?.message ?? response.statusText, json?.fieldErrors ?? {})
  }

  const totalHeader = response.headers.get('X-Total-Count')
  return { data: json as T, total: totalHeader ? Number(totalHeader) : null }
}

export const api = {
  get: <T>(path: string) => request<T>(path).then((r) => r.data),
  list: <T>(path: string) => request<T[]>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, 'POST', body).then((r) => r.data),
  patch: <T>(path: string, body?: unknown) => request<T>(path, 'PATCH', body).then((r) => r.data),
}