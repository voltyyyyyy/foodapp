# Staff portal

The browser application lives here, separate from Android and the Python backend. FastAPI serves it at `/staff/`; its administrator API calls stay on the same origin.

- `index.html`: document shell.
- `portal.js`: Email sign-in, record lists, editing, reports and administration workflows.
- `portal.css`: responsive layout, typography and accessible focus states.
- `atkinson.ttf` / `FONT-LICENSE.txt`: bundled font and license.

Run `./tools/start-local.ps1` from the repository root and open http://localhost:8000/staff/. No npm installation or frontend build is required. Syntax check: `node --check staff-portal/portal.js`.

See [staff workflows and deployment](../docs/operations/STAFF_PORTAL.md). The Docker image copies this directory to `/app/staff-portal` and sets `STAFF_PORTAL_DIR`. Local Python runs discover this directory relative to the repository root.
