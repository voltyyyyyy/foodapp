# Agent guide: FoodBridge Navigator

This guide applies to the whole repository. Use it to find the right source files, run the application, and verify changes. Start with the user's current request and preserve unrelated work already present in the workspace.

## Product boundaries

FoodBridge helps people in Singapore find food support, view provider information, save listings and submit correction reports.

- **Android is user-facing only.** Browsing, bookmarks and reporting need no account. Do not reintroduce staff sign-in or administrator screens into Android.
- **Staff use the separate browser portal at `/staff/`.** Sign-in uses a **User ID**, represented by `username` in the API, not an email address.
- Public information and staff operations share the same backend and database. Maintain server-side authorisation for administrator endpoints.

## Where to work

| Location | Responsibility |
| --- | --- |
| `android/app/src/main/java/sg/foodbridge/navigator/` | Native Kotlin / Jetpack Compose application |
| `MainActivity.kt` | App shell, navigation and shared/public screens |
| `AppViewModel.kt` | UI state, discovery, routing and report submission |
| `Repository.kt` | API requests, local preferences and approved-data cache |
| `FoodMap.kt`, `SearchSuggestions.kt` | Google Maps, location and address/organisation search |
| `ServiceDetails.kt`, `Providers.kt` | Listing details, distribution information and provider directory |
| `backend/app/main.py` | FastAPI endpoints, authorisation, moderation, publishing and audit transactions |
| `backend/app/domain.py` | Pydantic validation, schedules, availability and search calculations |
| `backend/app/db.py` | SQLAlchemy models, database engine and sessions |
| `backend/app/onemap.py`, `google_routes.py` | External address and routing adapters |
| `backend/app/maintenance.py`, `scheduler.py` | Expiry, session cleanup, backup scheduling and operational status |
| `backend/app/manage_admin.py` | Operator CLI for creating/resetting administrator accounts |
| `backend/seeds/` | Reviewed import data; retain its source and verification notes |
| `backend/tests/` | Executable acceptance suites using disposable databases |
| `staff-portal/` | Plain HTML/CSS/JavaScript administrator application; no npm build |
| `tools/` | Stable PowerShell setup, build, startup and import commands |
| `docs/` | Architecture, setup, operations, verification, history and licenses |
| `reference/` | Original design exports, course documents and archived ZIP |

The Kotlin filenames above are relative to the Android source directory. Original references and historical handoffs are context, not authoritative descriptions of today's routes, account fields or directory structure.

## Start and inspect the local stack

Use PowerShell from the repository root unless a command explicitly changes directory.

```powershell
./tools/start-local.ps1
```

Docker Desktop must be running. The script builds the API and portal, starts PostgreSQL, and starts scheduled maintenance. It recreates the task's API/maintenance containers while retaining database and backup volumes; a rebuild briefly interrupts those services. Check the existing runtime before restarting it unnecessarily.

| Surface | Address |
| --- | --- |
| Staff portal | `http://localhost:8000/staff/` |
| API documentation | `http://localhost:8000/docs` |
| Readiness / liveness | `/health/ready`, `/health/live` |
| Android emulator API | `http://10.0.2.2:8000` |

Local container names are `foodbridge-api`, `foodbridge-postgres` and `foodbridge-maintenance`. Inspect with `docker ps` and `docker logs --tail 30 foodbridge-api`; do not dump container environment variables containing secrets.

The local User ID and password are stored in ignored `backend/data/local-staff-user-id.txt` and `backend/data/local-staff-password.txt`. Do not copy credentials into source, guides, screenshots or test fixtures. Environment bootstrap settings create missing accounts; changing them does not reset existing passwords. The account-management CLI resets an existing account and revokes its sessions, so use it only when account creation/reset is part of the requested work.

For a Python-only setup, follow the root [README](README.md). Run Uvicorn from `backend/`: the default SQLite path is relative to the process working directory. Production requires PostgreSQL.

## Build and deployment paths

```powershell
# Uses the downloaded toolchain in ignored tools/.local/
./tools/build-android.ps1

# Override the API URL when needed
./tools/build-android.ps1 -ApiUrl 'https://YOUR_API_HOST'

# Docker build context is the repository root, not backend/
docker build -f backend/Dockerfile -t foodbridge-api .
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. The project uses Java 17, Android minimum API 31 and compile/target API 35. Inspect Gradle configuration before changing versions. Use `./tools/setup-android.ps1` only if the local toolchain needs installation or repair.

FastAPI serves `staff-portal/` directly. Docker copies it to `/app/staff-portal` and sets `STAFF_PORTAL_DIR`; local Python resolves it from the repository layout. Keep the portal and `/api/` on the same origin. Its content security policy permits local assets, so inline scripts or remote fonts require corresponding security/design changes.

The root `.dockerignore` deliberately admits only backend application source and portal assets. If new runtime files are added elsewhere, update its allowlist and Dockerfile together. Local data, credentials, virtual environments, reference archives and Android build output must stay outside the image.

## Behaviour to preserve

### Discovery and trustworthy information

- Address searches in Android show up to ten nearest matching services, ranked by geographic distance. Apply suitability filters before ranking; dietary requirements use AND matching. Do not silently relax filters.
- Empty **Find support** browses active support alphabetically without applying the nearest-ten cap or claiming a suitability match. Organisation-name search and provider browsing also have distinct meanings from address-based matching.
- A provider contact or office is not automatically a collection point. Preserve the distinction between provider directory records and confirmed service locations.
- Unknown hours, diets, access, stock and delivery arrangements remain explicit. Opening hours do not prove food availability; old programme frequency is not evidence for a dated appointment.
- Retain source, import time and last verification time. A save must not imply new verification without evidence. Treat reviewed seeds as sourced inputs, not a complete live Singapore directory.
- Device origins and route results remain memory-only. Preserve foreground permission handling, denied-location recovery, offline approved snapshots and honest external-service failure messages.

### Administration: UC-06 and UC-07

- Viewing a correction report is read-only. Show reference, category, description, submission time, status and current related information; retain access to assessed reports.
- Validate proposed updates on the server. Save the approved record and its audit entry in the **same transaction**. An audit failure must roll back the record update.
- Record administrator, action, target and timestamp. Retain before/after values where the existing workflow does so.
- Require the reviewed record version; reject stale edits instead of overwriting another administrator's work.
- Preserve form input after validation/network/database errors and allow retry. Cancellation discards unsaved changes. Session recovery must not lose the draft.
- Pending imports stay private until reviewed and published. Publish organisations before their services; unresolved suspected duplicates block publication. Imports must not overwrite approved records.
- Correction approval can publish the related change and assess the report atomically. An independent related-record edit does not automatically assess the report.

`Record` stores entity kind, state, version, timestamps and JSON data. `Base.metadata.create_all` creates missing tables; it does not migrate existing schemas. Plan migrations explicitly when changing persisted structures. `Admin.username` and `Token.username` intentionally map to legacy physical columns named `email`; retain that compatibility unless implementing a migration.

## Verify the change

Choose checks relevant to the files and behaviour changed:

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_backend.py
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
node --check staff-portal/portal.js
./tools/build-android.ps1
git diff --check
```

These Python suites are standalone executable checks, not a pytest workflow. They create disposable databases and do not require the live Docker stack. `verify_backend.py` also measures local concurrent search performance and rewrites a result file under `docs/verification/`.

`TEST_DATABASE_URL`, when set, must point to an explicitly disposable PostgreSQL test database. Never point it at the running FoodBridge database. Demo fixtures are for isolated tests; keep `DEMO_DATA=false` in the user-facing stack.

For portal changes, also check desktop/mobile layout, keyboard focus, search/filtering, edit/cancel, error input retention and expired-session recovery when browser tooling is available. For Android UI changes, verify the affected workflow on an emulator/device when available. A syntax check or successful build is not visual verification; state any unverified UI behaviour plainly. Documentation-only changes need link/path checks rather than an application rebuild.

## Working conventions

- Check `git status --short` before editing. Preserve unrelated changes, seed updates, ignored credentials and live database contents.
- Keep existing tool entry points stable. If files move, update source paths, tests, Docker/Compose configuration and documentation links together.
- Use reviewed imports and audited APIs for listing publication. Do not directly rewrite live records to bypass moderation or provenance.
- Never remove persistent volumes, reset the live database or replace credentials as incidental cleanup.
- Prefer the established Kotlin/Compose, Python/FastAPI and plain browser JavaScript patterns. Introduce frameworks or dependencies only when the requested change benefits from them.
- Keep UI language plain, readable and accessible. Show unknown/failure states honestly rather than inventing success or availability.
- Report what changed, the checks actually run, and any material limitations. Local acceptance/performance results do not prove production uptime or user completion targets.

## Further reading

- [Documentation index](docs/README.md)
- [Architecture and requirement mapping](docs/architecture/BUILD_PLAN.md)
- [Design decisions](docs/architecture/DESIGN_REFINEMENT.md)
- [Staff workflows and deployment](docs/operations/STAFF_PORTAL.md)
- [Release operations and backups](docs/operations/OPERATIONS.md)
- [Google Maps configuration](docs/setup/GOOGLE_MAPS_SETUP.md)
- [FoodConnect source/import notes](docs/setup/FOODCONNECT_IMPORT.md)
- [Acceptance suite instructions](backend/tests/README.md)
