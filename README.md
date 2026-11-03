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
$env:DB_PASSWORD = "<your-local-db-password>"
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

The response contains an access token, `Bearer` token type, and expiry timestamp. The JWT contains the user ID, tenant ID, and role claims.

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

The API returns `400` for invalid input, `401` for failed/missing authentication, `403` for forbidden access, `404` for a missing resource, `409` for duplicate data, and `500` for unexpected failures.

## Run tests

```powershell
.\mvnw.cmd test "-Dtest=ProjectServiceTests,TaskServiceTests"
```

The Mockito service tests need no database or Docker. The existing Testcontainers integration tests (`AuthFlowIntegrationTests`, `WorksphereApiApplicationTests`) require Docker Desktop and **cannot run on machines without virtualization**; they are kept but were not executed locally.

## Security notes

- Access tokens are signed HMAC JWTs and expire after 15 minutes by default.
- Send tokens only as `Authorization: Bearer <token>`.
- Use a unique JWT secret of at least 32 bytes; production secrets belong in a secret manager or deployment environment, never Git.
- The app is stateless: it does not create login sessions or use refresh tokens in Week 1.
- BCrypt hashes passwords; the raw password and hash are never included in API responses.

## Manual testing with Postman or VS Code REST Client

Open [`docs/api-tests.http`](docs/api-tests.http) with the VS Code *REST Client* extension (or copy the requests into Postman) and run top to bottom. It covers register, login, `/me`, projects, tasks, and a cross-tenant isolation section where tenant B must receive `404`/empty lists for tenant A's data. Change the slugs/emails between runs to avoid `409` on re-registration.