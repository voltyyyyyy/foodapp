# FoodBridge staff portal

Android is exclusively for finding, saving and reporting food support. Staff operations live in the browser at `/staff/`. The portal uses the same API and database as Android; saving approved information immediately makes it available to the public API. No frontend build tool, external font service or separate web server is required.

## Start locally

Run `./tools/start-local.ps1` with Docker Desktop running, then open **http://localhost:8000/staff/**. Use the User ID in `backend/data/local-staff-user-id.txt`; the local password is in the ignored file `backend/data/local-staff-password.txt`. The script rebuilds the API image including the portal while preserving the database volume. The Python-only run instructions in the README also serve the portal.

## Staff workflows

Correction reports show pending and assessed items together, with search, status filters and pages of 20 records. Each row identifies the report, category, description, submission time and status. Open a report to load its latest stored details and related listing. Viewing changes neither data nor status. **Update related information** starts an independent listing edit; **Edit, approve and publish** updates the listing and approves the report in one transaction. Rejecting requires a reason. An assessed report retains the assessor, time and reason.

Services, provider directory and organisations each have searchable record lists. Record details show stored values, source and verification time. **Edit information** opens the current values. Services have controls for dietary options, support type, contact, address, coordinates, active state, delivery arrangements and recurring hours. Date-specific hours and availability evidence are available as structured advanced fields. Providers have contact and verification controls, plus expandable programme fields for delivery methods, diets and recipient information. Unedited facts are preserved. Cancellation discards unsaved form values. Verification time is entered explicitly; saving does not falsely mark the entire listing as newly verified.

Saving an approved record publishes its revised values. Saving a pending imported record keeps it private until **Publish reviewed record** succeeds. Imported organisations must be published before their services, and suspected duplicates must be resolved first. Use **Add / import records** to create an organisation or upload/paste a permissioned JSON export. Review imports in Services / Providers / Organisations, invalid rows in Quarantine, source outcomes in Source health, and backups in Operations. Quarantine corrections are re-imported rather than overwriting failed source evidence. Audit log displays the latest 200 entries with before/after values.

Errors preserve form input and permit retry. A conflicting edit is rejected instead of overwriting another administrator's change. **Reload latest information** explicitly asks before discarding the unsaved form. When a session expires, sign in again in the recovery form, then retry the original action. Tokens exist only in browser memory; refresh or closing the tab requires a new sign-in. Data endpoints enforce administrator authorisation; downloading the login page grants no access to staff records.

## Deploy

Build from the repository root: `docker build -f backend/Dockerfile -t foodbridge-api .`. The image includes `staff-portal/` and the backend. Deploy that image with PostgreSQL and these environment variables in the host's secret/configuration settings:

| Variable | Value |
| --- | --- |
| `ENVIRONMENT` | `production` |
| `DATABASE_URL` | `postgresql+psycopg://USER:PASSWORD@HOST:5432/DATABASE` |
| `ADMIN_USERNAME` | Operator-selected administrator user ID |
| `ADMIN_PASSWORD` | Unique password of at least 12 characters |
| `DEMO_DATA` | `false` |

Credentials provision an account on first startup and do not reset an existing account. The deployment configuration contains no real credentials. The Docker image listens on port 8000; route HTTPS ingress to it and open `https://YOUR_HOST/staff/`. Keep `/api/` on the same origin. `compose.yaml` includes PostgreSQL and scheduled maintenance; set `DB_PASSWORD`, `ADMIN_USERNAME`, and `ADMIN_PASSWORD` in deployment secrets and run `docker compose up -d --build`. Provide persistent database/backup volumes and configure HTTPS at your ingress. See [OPERATIONS.md](OPERATIONS.md) for backups, monitoring and release requirements. No cloud deployment or hosting account is provisioned by these code changes.

The portal serves only local scripts/fonts, uses a restrictive content security policy, prevents framing, and marks responses no-store. API rate limiting and eight-hour session expiration apply. Public Android endpoints remain anonymous. All provisioned administrator accounts share the same administrator permissions; account provisioning is an operator task rather than an in-portal invitation workflow.

To provision another administrator, run `docker exec -it foodbridge-api python -m app.manage_admin --user-id USER_ID` and enter the password at the hidden prompt. Reusing an existing User ID resets its password and revokes that user's active sessions. Account creation/reset records an audit entry without storing passwords in the audit log. No email address is required. Existing database column names are retained internally for compatibility with deployed data; application identity and sign-in use `username`.

## Acceptance

Run:

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
./backend/.venv/Scripts/python.exe backend/tests/verify_backend.py
node --check staff-portal/portal.js
./tools/build-android.ps1
```

The staff checks use a disposable database under ignored `backend/data/acceptance`. They cover anonymous denial, empty and complete report views, read-only viewing, latest related values, validated publishing, source metadata, audit contents, stale edits, atomic rollback on simulated database failure, retry, assessed reports and session revocation. They do not modify the running database. Before public release, also exercise the portal in desktop/mobile browsers, keyboard navigation, cancellation, invalid input retention, session recovery, and failed-network retry.
