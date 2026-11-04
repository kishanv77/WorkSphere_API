# WorkSphere web app

React + TypeScript + Vite + Tailwind front end for the WorkSphere API.

## Run it

Start the API first (see the root README), then:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The app talks to `http://127.0.0.1:8080` by default; override it with a `VITE_API_URL` environment variable (for example in a git-ignored `.env.local`).

## Screens

- **Sign in / Create workspace**: registers a tenant and its first admin, then signs in.
- **Dashboard**: task counts, overdue tasks, progress bar, tasks assigned to you.
- **Projects**: card list; admins can create, edit and archive.
- **Task board**: To do / In progress / Done columns with drag and drop, project and assignee filters, new-task dialog, and a detail drawer with editing and comments.
- **Team**: member list; admins can add members and deactivate them.

Permissions mirror the API: only admins manage projects and users, and members can move or edit only tasks they created or are assigned to (other cards are not draggable). The API is the source of truth and enforces every rule.

## Notes

- The access token is kept in `localStorage`. When it expires or the user is deactivated, the API returns 401 and the app returns to the sign-in screen.
- `npm run build` type-checks and builds; `npm run lint` runs oxlint.