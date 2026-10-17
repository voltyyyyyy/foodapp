# FoodBridge Navigator

Native Android 12+ food-support discovery with registered-user sign-in and a separate browser staff portal. The revised SRS account setup, Google sign-in and email/staff migration instructions are in [Accounts](docs/setup/ACCOUNTS.md). The implementation plan, requirement mapping and reference contradictions are in [docs/architecture/BUILD_PLAN.md](docs/architecture/BUILD_PLAN.md).

## Repository layout

```text
android/          Native user-facing Android application
backend/
  app/            API, domain models and maintenance jobs
  tests/          Isolated executable acceptance suites
  seeds/          Reviewed import data
staff-portal/     Browser application for administrators
tools/            Stable setup, build, run and import commands
docs/             Architecture, setup, operations, verification and history
reference/        Original design/course inputs and archives
compose.yaml      PostgreSQL, API and maintenance deployment
```

See the [documentation index](docs/README.md), [test instructions](backend/tests/README.md), and [tool guide](tools/README.md). Local databases, credentials, downloaded SDKs and build outputs are ignored rather than mixed into application source.

Coding agents should read the repository [agent guide](AGENTS.md) before making changes.

## Run locally

The complete local service stack uses Docker Desktop:

```powershell
./tools/start-local.ps1
```

This starts PostgreSQL17, the API on localhost8000, and hourly expiry /20-hour backup maintenance. Task-owned containers are `foodbridge-postgres`, `foodbridge-api`, and `foodbridge-maintenance`. Data and backups use separate persistent volumes. Run the script again to rebuild the API without deleting the database. Secrets live in ignored `backend/data` files. Docker Desktop must be running.

The Android APK uses `http://10.0.2.2:8000` for an Android emulator. A physical device can use `adb reverse tcp:8000 tcp:8000` and an APK built with `-PapiUrl=http://127.0.0.1:8000`, or use a reachable HTTPS API URL.

```powershell
# From the repository root
uv venv backend/.venv
uv pip install --python backend/.venv/Scripts/python.exe -r backend/requirements.txt
cd backend
$env:ADMIN_EMAIL = 'admin@example.org'
$env:ADMIN_PASSWORD = '<a unique password of at least 12 characters>'
.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log
```

The staff portal is at **http://localhost:8000/staff/** and signs in with an **email address**. Android provides registered-user sign-in, registration, password recovery, Google sign-in and profile management; staff moderation remains in the browser portal. Configure SMTP and Google OAuth before using external account services. Existing User ID administrators need an explicit email mapping using the migration command in [Accounts](docs/setup/ACCOUNTS.md). Local passwords and email configuration stay in ignored `backend/data` files; bootstrap settings never reset existing credentials.

```powershell
# Install the repo-local JDK, Gradle and Android SDK
./tools/setup-android.ps1
# Build the debug APK
./tools/build-android.ps1
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. Open the `android` directory in Android Studio to run an emulator or attach a device. A debug build permits the local HTTP API; release builds require HTTPS. Demo seeding is disabled. Only actual listings are public; cached fictional fixtures are excluded. Try postal code `560123`, or a Singapore address/device location. Dietary filters use AND matching; results can be empty when selected needs cannot be confirmed.

Press **Find support** with an empty field to browse **all active food support** alphabetically, including provider contacts without confirmed map locations. This browse view does not apply suitability filters or a10-result limit.

Find and Map accept **an address or organisation name**. Name suggestions search all imported provider contacts and service/organisation names; they open information without claiming suitability. Choose **Search this as an address** to resolve ambiguous input. An address or postal code returns up to **10 nearest matching services**, ordered by straight-line distance. The initial search area covers Singapore; optional radius and suitability filters narrow results. Fewer than10 are shown when fewer services qualify.

### Google Maps

Map opens directly to Singapore with the existing floating address/organisation search, saved suggestions, filters, current-location action, service pins and preview. Google Maps SDK for Android supplies the custom styled map; OneMap supplies address lookup and **Walk, Public transport, Drive** routes. Configure `ONEMAP_EMAIL` and `ONEMAP_PASSWORD` on the API. The Google Routes adapter is retained as historical code but is no longer used by the route endpoint. See [Google Maps setup](docs/setup/GOOGLE_MAPS_SETUP.md) for the two restricted keys and exact local file paths.

The manually reviewed seed in `backend/seeds/verified-services.json` contains public contact/application facts from the [Willing Hearts provider page](https://willinghearts.org.sg/contact), with coordinates resolved through OneMap. It identifies a meal-delivery coordination site, not a confirmed walk-in collection point. Hours, diets, accessibility and live availability remain unknown. After starting the local stack, run `./tools/import-reviewed-seed.ps1` to import and publish it through the audited staff APIs. This is a small project seed, not a complete verified Singapore directory or a partner feed. The current workspace already has it published.

OneMap geocoding is attempted remotely with a local approved-address fallback. **Get directions** opens Map with the destination, route line, journey summary and optional route steps. Map uses device location by default when foreground access is already granted. Missing access, disabled location or a failed fix opens a starting-location sheet with device-location permission and address entry. Directions use a fresh device location by default; edit the start row to use another address. All three modes require the backend Google Routes key; the map separately requires the Android Google Maps key. Routes and origin coordinates remain memory-only. No live Google routing is claimed until credentials are configured and the app is reviewed.

### FoodConnect provider directory

Find's **Browse food support providers** and Map's **Providers** action open a searchable directory with the FoodConnect categories, contact/application information and source notes. Provider contacts can exist without collection coordinates; no office is assumed to be a food pickup point. Provider-reviewed and directory-only facts are distinguished, with unknown suitability/stock retained. Support types have icon badges; expandable programme cards separate recipients, delivery methods, regions, dietary options and dates into labelled rows. Public directory GET `/api/providers` returns only active approved records; staff can review/edit/publish in the browser portal's **Provider directory**.

The reviewed seed is `backend/seeds/foodconnect-providers.json`; import it with `./tools/import-foodconnect.ps1` after starting the API. Coverage, verification and contradictions are documented in [docs/setup/FOODCONNECT_IMPORT.md](docs/setup/FOODCONNECT_IMPORT.md). The source is [FoodConnect](https://foodconnect.gov.sg/directory/), whose page footer dates its update to March 2023; listing facts should be confirmed with the provider. The local import does not imply live availability or a partner export.

The current local directory contains22 published providers (18 with provider sources reviewed,4 directory-only). A confirmed Krsna public meal-site seed can be recreated with `./tools/import-reviewed-seed.ps1 -SeedPath backend/seeds/foodconnect-map-services.json`. Directory contacts without confirmed service coordinates remain in the provider browser.

### Service details and corrections

Each service page separates availability, contact, eligibility, confirmed diets/accessibility, schedule, directions and sources. Directory distribution details show programme frequency, listed days/dates, recipients, dietary options, delivery/collection and areas, with source age and missing-data guidance. Real service locations are explicitly linked to their provider records. The week calendar supports previous/next week, Today and date selection; it applies separately verified collection / meal sessions and date-specific session overrides, including overnight sessions. Operating hours remain separate and do not prove that distribution is happening or food is available. Unknown schedules remain explicit; the calendar is expandable when only directory frequency is available, and exact sessions are never inferred from old frequency text. Provider listings retain programme frequency without inventing calendar appointments.

**Report incorrect information** opens a named-listing form from either a service or provider page. Choose a category, describe the issue (up to1000 characters), and optionally link evidence. Reports receive a reference and remain pending until staff review. Drafts are encrypted on this device per account, can be resumed after leaving, and are cleared after successful submission or explicit discard. Leaving a non-empty form offers keep/discard/cancel choices. Back returns to the original listing. The API accepts `target_kind` (`service` by default, or `provider`) alongside the backward-compatible `service_id` target field.

The local dataset has22 actual provider records and2 confirmed service locations. Fictional fixtures were withdrawn with an audit trail. To clean an older local database, run `docker exec foodbridge-api python -m app.retire_demo` after rebuilding; it preserves actual records and staff history.

### Saved reminders

Saved listings have optional local reminders at a chosen daily Singapore time, on recorded collection / meal session days while a session is upcoming or in progress. Unknown sessions are labelled "Schedule not confirmed"; office hours alone never enable a reminder. Android notification permission and the reminder channel must be allowed. Delivery is approximate and may be delayed by Android. Saved shows the last local cache update and warns that a reminder does not reserve food or confirm availability.

Reminder settings are separate by account and pause at sign-out or session expiry. Sign in and open Saved to resume them. Removing a bookmark or clearing that account?s local settings cancels its reminder. Local reminders use approved cached information; they do not establish current availability.

## Data and operations

API documentation: `http://localhost:8000/docs`. Database: PostgreSQL in the complete local stack; SQLite when using the Python-only alternative. Set `DATABASE_URL=postgresql+psycopg://...` for production PostgreSQL. Administrator UI includes reports, changes, canonical service edits, pending imported service publication, audit, duplicates, source health and quarantine. Import only authorised exports, and review each record before publishing.

```powershell
cd backend
.venv/Scripts/python.exe -m app.maintenance --backup-dir data/backups
```

For production deployment and outstanding evidence, read [docs/operations/OPERATIONS.md](docs/operations/OPERATIONS.md). This repository provides an application, not an already deployed public service. Monthly uptime and first-time user completion targets require real measurements.
