# WorkSphere web app

React + TypeScript + Vite + Tailwind front end for the WorkSphere API.

## Run it

Start the API first (see the root README), then:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The dev server proxies `/api` and `/actuator` to `http://127.0.0.1:8080`, so the app and API share one origin; point the proxy elsewhere with an `API_PROXY_TARGET` environment variable. In production, serve the built app and the API from the same site (the refresh cookie is `SameSite=Strict`). `VITE_API_URL` still overrides the API base URL, but a cross-site URL breaks session refresh.

## Screens

- **Sign in / Create workspace**: registers a tenant and its first admin, then signs in.
- **Dashboard**: task counts, overdue tasks, progress bar, tasks assigned to you.
- **Projects**: card list; admins can create, edit and archive.
- **Task board**: To do / In progress / Done columns with drag and drop, project and assignee filters, new-task dialog, and a detail drawer with editing and comments.
- **Team**: member list; admins can add members and deactivate them.

Permissions mirror the API: only admins manage projects and users, and members can move or edit only tasks they created or are assigned to (other cards are not draggable). The API is the source of truth and enforces every rule.

## Notes

- The access token (15 minutes) is kept only in memory. The refresh token lives in an httpOnly cookie that scripts cannot read. When an API call gets a 401, the app refreshes once and retries; reloading the page restores the session the same way. If refreshing fails (signed out, session older than 7 days, or user deactivated), the app returns to the sign-in screen.
- `npm run build` type-checks and builds; `npm run lint` runs oxlint.