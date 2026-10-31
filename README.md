# WorkSphere API

Week 1 of the WorkSphere multi-tenant SaaS backend. It provides tenant registration, password-based login, JWT access tokens, and an authenticated identity endpoint.

## Prerequisites

- JDK 25 on your `PATH` (`java -version`)
- Docker Desktop with Docker Compose (`docker compose version`)
- Git (optional, for source control)

The Maven Wrapper downloads Maven automatically; a separate Maven installation is not required.

## Local configuration

The application never stores secrets in source control. Set environment variables in the current PowerShell session before starting Docker or Spring Boot. Replace every angle-bracket value with your own local development value.

```powershell
$env:POSTGRES_USER = "<your-local-db-user>"
$env:POSTGRES_PASSWORD = "<your-local-db-password>"
$env:DB_USERNAME = $env:POSTGRES_USER
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
$env:JWT_SECRET = "<a-long-random-value-of-at-least-32-bytes>"
```

Optional variables:

- `POSTGRES_DB` — defaults to `worksphere`.
- `POSTGRES_PORT` — defaults to `5432`.
- `DB_URL` — defaults to `jdbc:postgresql://localhost:5432/worksphere`.

Do not commit `.env` files, database passwords, or JWT secrets. The `.gitignore` file excludes `.env`-style files.

## Start PostgreSQL

```powershell
docker compose up -d
docker compose ps
```

Compose creates one PostgreSQL 18 container and a named `postgres-data` volume. The database is available to Spring Boot at `localhost:5432` by default. The volume retains data when the container is stopped; use `docker compose down -v` only when you intentionally want to remove local database data.

## Start the application

After setting the environment variables and starting PostgreSQL:

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
.\mvnw.cmd test
```

The integration tests use Testcontainers to start a temporary PostgreSQL 18 container, apply Flyway migrations, and verify registration, duplicate handling, login, authentication, and `/me`. Docker Desktop must be running for these tests.

## Security notes

- Access tokens are signed HMAC JWTs and expire after 15 minutes by default.
- Send tokens only as `Authorization: Bearer <token>`.
- Use a unique JWT secret of at least 32 bytes; production secrets belong in a secret manager or deployment environment, never Git.
- The app is stateless: it does not create login sessions or use refresh tokens in Week 1.
- BCrypt hashes passwords; the raw password and hash are never included in API responses.

## Manual testing with Postman or VS Code REST Client

1. Run PostgreSQL and the application using the steps above.
2. Send the registration request and copy `userId`/`tenantId` from the response.
3. Send the login request and copy `accessToken`.
4. Send `GET /api/v1/auth/me` with `Authorization: Bearer <accessToken>`.
5. Confirm `/me` returns the same user and tenant IDs created during registration.
6. Repeat registration with the same email or slug to confirm a `409 Conflict` response.
