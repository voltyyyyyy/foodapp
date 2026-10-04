# FoodBridge Navigator

Native Android 12+ food-support discovery with an anonymous user experience and a separate authenticated browser staff portal. The implementation plan, requirement mapping and reference contradictions are in [docs/architecture/BUILD_PLAN.md](docs/architecture/BUILD_PLAN.md).

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
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD = '<a unique password of at least 12 characters>'
.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log
```

The staff portal is at **http://localhost:8000/staff/** and signs in with a **User ID**, without requiring an email. Android is user-facing only and contains no staff sign-in or administrative screens. The current local installation has a staff password in ignored `backend/data/local-staff-password.txt`. It is not committed. The local User ID is stored in ignored `backend/data/local-staff-user-id.txt` (defaults to `admin` on a new setup). Accounts are provisioned only on first startup; changing the environment password does not silently reset an existing account. See [staff portal deployment and acceptance](docs/operations/STAFF_PORTAL.md).

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

Map opens directly to Singapore with the existing floating address/organisation search, saved suggestions, filters, current-location action, service pins and preview. Google Maps SDK for Android supplies the custom styled map; backend Google Routes supplies **Walk, Public transport, Drive** routes. OneMap continues Singapore address lookup. See [Google Maps setup](docs/setup/GOOGLE_MAPS_SETUP.md) for the two restricted keys and exact local file paths.

The manually reviewed seed in `backend/seeds/verified-services.json` contains public contact/application facts from the [Willing Hearts provider page](https://willinghearts.org.sg/contact), with coordinates resolved through OneMap. It identifies a meal-delivery coordination site, not a confirmed walk-in collection point. Hours, diets, accessibility and live availability remain unknown. After starting the local stack, run `./tools/import-reviewed-seed.ps1` to import and publish it through the audited staff APIs. This is a small project seed, not a complete verified Singapore directory or a partner feed. The current workspace already has it published.

OneMap geocoding is attempted remotely with a local approved-address fallback. **Get directions** opens Map with the destination, route line, journey summary and optional route steps. Map uses device location by default when foreground access is already granted. Missing access, disabled location or a failed fix opens a starting-location sheet with device-location permission and address entry. Directions use a fresh device location by default; edit the start row to use another address. All three modes require the backend Google Routes key; the map separately requires the Android Google Maps key. Routes and origin coordinates remain memory-only. No live Google routing is claimed until credentials are configured and the app is reviewed.

### FoodConnect provider directory

Find's **Browse food support providers** and Map's **Providers** action open a searchable directory with the FoodConnect categories, contact/application information and source notes. Provider contacts can exist without collection coordinates; no office is assumed to be a food pickup point. Provider-reviewed and directory-only facts are distinguished, with unknown suitability/stock retained. Support types have icon badges; expandable programme cards separate recipients, delivery methods, regions, dietary options and dates into labelled rows. Public directory GET `/api/providers` returns only active approved records; staff can review/edit/publish in the browser portal's **Provider directory**.

The reviewed seed is `backend/seeds/foodconnect-providers.json`; import it with `./tools/import-foodconnect.ps1` after starting the API. Coverage, verification and contradictions are documented in [docs/setup/FOODCONNECT_IMPORT.md](docs/setup/FOODCONNECT_IMPORT.md). The source is [FoodConnect](https://foodconnect.gov.sg/directory/), whose page footer dates its update to March 2023; listing facts should be confirmed with the provider. The local import does not imply live availability or a partner export.

The current local directory contains22 published providers (18 with provider sources reviewed,4 directory-only). A confirmed Krsna public meal-site seed can be recreated with `./tools/import-reviewed-seed.ps1 -SeedPath backend/seeds/foodconnect-map-services.json`. Directory contacts without confirmed service coordinates remain in the provider browser.

### Service details and corrections

Each service page separates availability, contact, eligibility, confirmed diets/accessibility, schedule, directions and sources. Directory distribution details show programme frequency, listed days/dates, recipients, dietary options, delivery/collection and areas, with source age and missing-data guidance. Real service locations are explicitly linked to their provider records. The week calendar supports previous/next week, Today and date selection; it applies recurring hours and date-specific overrides, including overnight sessions. Recorded hours do not prove food availability. Unknown schedules remain explicit; the calendar is expandable when only directory frequency is available, and exact sessions are never inferred from old frequency text. Provider listings retain programme frequency without inventing calendar appointments.

**Report incorrect information** opens a named-listing form from either a service or provider page. Choose a category, describe the issue (up to1000 characters), and optionally link evidence. Reports receive a reference and remain pending until staff review. Back returns to the original listing. The API accepts `target_kind` (`service` by default, or `provider`) alongside the backward-compatible `service_id` target field.

The local dataset has22 actual provider records and2 confirmed service locations. Fictional fixtures were withdrawn with an audit trail. To clean an older local database, run `docker exec foodbridge-api python -m app.retire_demo` after rebuilding; it preserves actual records and staff history.

## Data and operations

API documentation: `http://localhost:8000/docs`. Database: PostgreSQL in the complete local stack; SQLite when using the Python-only alternative. Set `DATABASE_URL=postgresql+psycopg://...` for production PostgreSQL. Administrator UI includes reports, changes, canonical service edits, pending imported service publication, audit, duplicates, source health and quarantine. Import only authorised exports, and review each record before publishing.

```powershell
cd backend
.venv/Scripts/python.exe -m app.maintenance --backup-dir data/backups
```

For production deployment and outstanding evidence, read [docs/operations/OPERATIONS.md](docs/operations/OPERATIONS.md). This repository provides an application, not an already deployed public service. Monthly uptime and first-time user completion targets require real measurements.
