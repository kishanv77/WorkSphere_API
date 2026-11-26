// Same-origin by default: in development Vite proxies /api to the backend (see vite.config.ts).
const BASE: string = import.meta.env.VITE_API_URL ?? ''

// Tokens issued before refresh tokens existed were kept here; remove them.
try {
  localStorage.removeItem('worksphere.token')
} catch {
  /* storage unavailable */
}

/**
 * The access token lives only in memory, so injected scripts cannot lift it from storage.
 * After a reload the session is restored from the httpOnly refresh cookie via refreshSession().
 */
let accessToken: string | null = null

export const tokenStore = {
  get: (): string | null => accessToken,
  set(token: string) {
    accessToken = token
  },
  clear() {
    accessToken = null
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

interface LoginResponse {
  accessToken: string
}

async function send(path: string, method: string, body: unknown, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  try {
    return await fetch(BASE + path, {
      method,
      headers,
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the API running?')
  }
}

async function parse<T>(response: Response): Promise<Result<T>> {
  const text = await response.text()
  let json: { message?: string; fieldErrors?: Record<string, string> } | null = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
  }
  if (!response.ok) {
    throw new ApiError(response.status, json?.message ?? response.statusText, json?.fieldErrors ?? {})
  }
  const totalHeader = response.headers.get('X-Total-Count')
  return { data: json as T, total: totalHeader ? Number(totalHeader) : null }
}

const PUBLIC_AUTH_PATHS = new Set([
  '/api/v1/auth/login',
  '/api/v1/auth/register-tenant',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
])

let refreshInFlight: Promise<boolean> | null = null

async function attemptRefresh(): Promise<boolean> {
  const response = await send('/api/v1/auth/refresh', 'POST', undefined, null)
  if (!response.ok) return false
  const { data } = await parse<LoginResponse>(response)
  tokenStore.set(data.accessToken)
  return true
}

/**
 * Gets a new access token from the refresh cookie. Concurrent callers share one request, because each
 * refresh rotates the cookie. If another tab rotated it at the same moment, one retry picks up the new cookie.
 */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      return (await attemptRefresh()) || (await attemptRefresh())
    } catch {
      return false
    } finally {
      refreshInFlight = null
    }
  })()
  return refreshInFlight
}

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<Result<T>> {
  const token = tokenStore.get()
  let response = await send(path, method, body, token)

  // The access token is short-lived: on 401, refresh once and retry before giving up.
  if (response.status === 401 && token && !PUBLIC_AUTH_PATHS.has(path)) {
    if (await refreshSession()) {
      response = await send(path, method, body, tokenStore.get())
    }
    if (response.status === 401) {
      tokenStore.clear()
      window.dispatchEvent(new Event('auth:expired'))
    }
  }
  return parse<T>(response)
}

export const api = {
  get: <T>(path: string) => request<T>(path).then((r) => r.data),
  list: <T>(path: string) => request<T[]>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, 'POST', body).then((r) => r.data),
  patch: <T>(path: string, body?: unknown) => request<T>(path, 'PATCH', body).then((r) => r.data),
}
