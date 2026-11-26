# WorkSphere API

WorkSphere multi-tenant SaaS backend: tenant registration, JWT login, tenant-scoped projects and tasks.

## Prerequisites

- JDK 25 (`java -version`). If `JAVA_HOME` points at another JDK, set it to JDK 25 for the session.
- PostgreSQL 18 running natively on `localhost:5432`
- Maven Wrapper (included; no separate Maven install needed)
- Git

## Native PostgreSQL setup (primary)

Create the role and database once, as a PostgreSQL superuser (for example with `psql -U postgres`):

```sql
CREATE ROLE worksphere_app LOGIN PASSWORD '<choose-a-local-password>';
CREATE DATABASE worksphere OWNER worksphere_app;
```

Flyway creates the schema on first startup. Hibernate only validates it (`ddl-auto=validate`).

## Local configuration

Set these in the PowerShell session that runs the app. Never commit real values or `.env` files.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/worksphere"
$env:DB_USERNAME = "worksphere_app"
$env:DB_PASSWORD = "DaRkKnIgHt@7"
$env:JWT_SECRET = "<base64-random-value-of-at-least-32-bytes>"
```

Generate a secret with: `[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])`

### Optional: Docker

`compose.yaml` can start PostgreSQL in a container (`docker compose up -d`) if Docker is available. It is not required; the native setup above is the supported default.
## Start the application

After setting the environment variables:

```powershell
.\mvnw.cmd spring-boot:run
```

At startup, Flyway applies the versioned migrations in `src/main/resources/db/migration`. Hibernate uses `ddl-auto=validate`, so it checks mappings but never creates or changes the real schema.

Health check:

```powershell
curl.exe http://localhost:8080/actuator/health
```

## API

### Register a tenant

`POST /api/v1/auth/register-tenant`

```json
{
  "tenantName": "Acme Corporation",
  "tenantSlug": "acme-corp",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "email": "ada@example.test",
  "password": "Use-a-unique-local-password"
}
```

The response is `201 Created` and contains safe tenant/user identifiers. The first user is always `ACTIVE` with the `TENANT_ADMIN` role; its password is stored only as a BCrypt hash.

### Log in

`POST /api/v1/auth/login`

```json
{
  "email": "ada@example.test",
  "password": "Use-a-unique-local-password"
}
```

The response contains an access token, `Bearer` token type, and expiry timestamp. The JWT contains the user ID, tenant ID, and role claims. The response also sets a refresh token cookie (`ws_refresh`: `HttpOnly`, `Secure`, `SameSite=Strict`, path `/api/v1/auth`, valid 7 days).

After 5 failed logins for one email, or 20 from one client address, within 15 minutes, further attempts return `429 Too Many Requests` with a `Retry-After` header (in seconds). The counters are kept in memory per instance. Behind a reverse proxy, set `server.forward-headers-strategy=native` so the real client address is used.

### Refresh and log out

- `POST /api/v1/auth/refresh` (no bearer token needed): exchanges the refresh cookie for a new access token, returned in the same shape as login. The cookie is **rotated on every call**, so the old one stops working. If a rotated-out token is presented again more than 30 seconds later, it is treated as stolen and all of that user's sessions are ended. Missing, expired, revoked or deactivated-user tokens return `401`.
- `POST /api/v1/auth/logout`: revokes the refresh cookie (if any) and clears it. Always `204`.

### Get the current identity

`GET /api/v1/auth/me`

```http
Authorization: Bearer <access-token>
```

The response includes user ID, tenant ID, email, first name, last name, and role. Tenant identity comes from the verified token, never from a request parameter.

### Projects (Bearer token required; tenant comes from the token only)

```powershell
$h = @{ Authorization = "Bearer $token" }
$p = Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/v1/projects -Headers $h -ContentType 'application/json' -Body '{"name":"Website Redesign","description":"Q4"}'
Invoke-RestMethod http://127.0.0.1:8080/api/v1/projects -Headers $h
Invoke-RestMethod http://127.0.0.1:8080/api/v1/projects/$($p.id) -Headers $h
Invoke-RestMethod -Method Patch http://127.0.0.1:8080/api/v1/projects/$($p.id) -Headers $h -ContentType 'application/json' -Body '{"name":"Renamed"}'
Invoke-RestMethod -Method Patch http://127.0.0.1:8080/api/v1/projects/$($p.id)/archive -Headers $h
```

Duplicate names (case-insensitive) within a tenant return `409`. Another tenant's project returns `404`. Archiving is idempotent and projects are never physically deleted.

### Tasks

```powershell
$t = Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/v1/tasks -Headers $h -ContentType 'application/json' -Body (@{ projectId = $p.id; title = "Draft wireframes"; priority = "HIGH"; dueDate = "2026-12-01" } | ConvertTo-Json)
Invoke-RestMethod "http://127.0.0.1:8080/api/v1/tasks?projectId=$($p.id)&status=TODO" -Headers $h
Invoke-RestMethod http://127.0.0.1:8080/api/v1/tasks/$($t.id) -Headers $h
Invoke-RestMethod -Method Patch http://127.0.0.1:8080/api/v1/tasks/$($t.id) -Headers $h -ContentType 'application/json' -Body '{"title":"Wireframes v2"}'
Invoke-RestMethod -Method Patch http://127.0.0.1:8080/api/v1/tasks/$($t.id)/status -Headers $h -ContentType 'application/json' -Body '{"status":"IN_PROGRESS"}'
```

- `POST /api/v1/tasks` needs `projectId` and `title`; optional `assigneeId` (a user in your tenant), `description`, `priority` (`LOW|MEDIUM|HIGH`, default `MEDIUM`), `dueDate`. Tasks start as `TODO`.
- `PATCH /api/v1/tasks/{id}` is a partial update; send `"unassign": true` or `"clearDueDate": true` to clear those fields.
- List filters: `projectId`, `status` (`TODO|IN_PROGRESS|DONE`), `assigneeId`.
- Unknown or other-tenant project/assignee/task IDs return `404`; adding tasks to an archived project returns `409`.

### Users (tenant team management)

- `POST /api/v1/users` (`TENANT_ADMIN` only; `403` for others): creates an active `MEMBER` in your tenant. Body: `firstName`, `lastName`, `email`, `password` (12-72 chars). Role and tenant are never taken from the request. Duplicate email returns `409`.
- `GET /api/v1/users`, `GET /api/v1/users/{userId}`: any authenticated user, tenant-scoped (`404` across tenants).
- `PATCH /api/v1/users/{userId}/deactivate` (`TENANT_ADMIN` only): idempotent; an admin cannot deactivate themselves (`409`). Deactivated users cannot log in or be assigned tasks, and all of their refresh tokens are revoked.

### Roles and pagination

- **Roles:** `TENANT_ADMIN` can create, update and archive projects and manage users. `MEMBER` can read projects and users and create, update and change the status of tasks. Forbidden actions return a JSON `403`.
- **Pagination:** `GET /api/v1/projects`, `/tasks` and `/users` accept `page` (zero-based, default `0`) and `size` (default `20`, maximum `100`; larger values are clamped). The response is still a plain JSON array; the total number of matching records is in the `X-Total-Count` response header. Example: `curl.exe -i "http://127.0.0.1:8080/api/v1/projects?page=0&size=2" -H "Authorization: Bearer <token>"`.

### Task comments, dashboard and task permissions

- `POST /api/v1/tasks/{taskId}/comments` (body `{ "body": "..." }`, max 2000 chars) and `GET /api/v1/tasks/{taskId}/comments` (paged, oldest first, `X-Total-Count`): any authenticated user in the tenant. Comments on another tenant's task return `404`.
- `GET /api/v1/dashboard`: `tasksByStatus`, `overdueTasks` (due before today and not `DONE`), `myOpenTasks` (assigned to you, not `DONE`), `activeProjects`, `archivedProjects`, `activeMembers`. Always scoped to your tenant.
- **Task permissions:** `TENANT_ADMIN` can change any task. A `MEMBER` can edit a task or change its status only if they created it or are assigned to it; otherwise `403`.
- **Immediate deactivation:** every request re-checks that the token's user is still active, so a deactivated user's token stops working at once (`401`).
- **CORS:** browsers may call `/api/**` from the origins in `app.cors.allowed-origins` (default `http://localhost:5173`), with credentials. `X-Total-Count` is exposed to the browser. The refresh cookie is `SameSite=Strict`, so the web app must be served from the same site as the API; in development the Vite dev server proxies `/api` for this.

## Web app (frontend)

A React + TypeScript + Vite + Tailwind client lives in [`frontend/`](frontend/README.md). With the API running:

```powershell
cd frontend
npm install
npm run dev
```

Then open http://localhost:5173. It needs Node.js 22 or newer.

## Consistent errors

Errors use one JSON shape:

```json
{
  "timestamp": "2026-08-23T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register-tenant",
  "fieldErrors": {
    "email": "Email must be valid"
  }
}
```

The API returns `400` for invalid input, `401` for failed/missing authentication, `403` for forbidden access, `404` for a missing resource, `409` for duplicate data, `429` for too many failed logins, and `500` for unexpected failures.

## Run tests

```powershell
# Unit tests: no database or Docker needed
.\mvnw.cmd test "-Dtest=*Tests,!*IntegrationTests,!WorksphereApiApplicationTests"

# Everything, including the Testcontainers PostgreSQL integration tests (needs Docker)
.\mvnw.cmd test
```

CI runs both: a fast unit-test job and a full-suite job (GitHub's Ubuntu runners provide Docker).

## Security notes

- Access tokens are signed HMAC JWTs and expire after 15 minutes by default.
- Send tokens only as `Authorization: Bearer <token>`.
- Use a unique JWT secret of at least 32 bytes; production secrets belong in a secret manager or deployment environment, never Git.
- Refresh tokens are random 256-bit values. Only their SHA-256 hash is stored (`refresh_tokens` table), they are rotated on every use, and expired ones are deleted daily. The browser keeps them in an `HttpOnly` cookie that scripts cannot read; the web app keeps access tokens in memory only.
- Apart from refresh tokens, the API is stateless: no server-side HTTP sessions.
- BCrypt hashes passwords; the raw password and hash are never included in API responses.

## Manual testing with Postman or VS Code REST Client

Open [`docs/api-tests.http`](docs/api-tests.http) with the VS Code *REST Client* extension (or copy the requests into Postman) and run top to bottom. It covers register, login, `/me`, projects, tasks, and a cross-tenant isolation section where tenant B must receive `404`/empty lists for tenant A's data. Change the slugs/emails between runs to avoid `409` on re-registration.
