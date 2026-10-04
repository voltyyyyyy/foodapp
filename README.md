# FoodBridge Navigator

Native Android 12+ food-support discovery with an anonymous seeker experience and authenticated staff operations. The implementation plan, requirement mapping and reference contradictions are in [docs/BUILD_PLAN.md](docs/BUILD_PLAN.md).

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
$env:ADMIN_EMAIL = 'admin@foodbridge.local'
$env:ADMIN_PASSWORD = '<a unique password of at least 12 characters>'
.venv/Scripts/python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log
```

The current local installation has a generated staff password in ignored `backend/data/local-admin-password.txt`. It is not committed. Staff sign-in is under Settings. Accounts are provisioned only on first startup; changing the environment password does not silently reset an existing account.

```powershell
# Install the repo-local JDK, Gradle and Android SDK
./tools/setup-android.ps1
# Build the debug APK
./tools/build-android.ps1
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. Open the `android` directory in Android Studio to run an emulator or attach a device. A debug build permits the local HTTP API; release builds require HTTPS. Demo seeding is disabled. Only actual listings are public; cached fictional fixtures are excluded. Try postal code `560123`, or a Singapore address/device location. Dietary filters use AND matching; results can be empty when selected needs cannot be confirmed.

Press **Find support** with an empty field to browse **all active food support** alphabetically, including provider contacts without confirmed map locations. This browse view does not apply suitability filters or a10-result limit.

Find and Map accept **an address or organisation name**. Name suggestions search all imported provider contacts and service/organisation names; they open information without claiming suitability. Choose **Search this as an address** to resolve ambiguous input. An address or postal code returns up to **10 nearest matching services**, ordered by straight-line distance. The initial search area covers Singapore; optional radius and suitability filters narrow results. Fewer than10 are shown when fewer services qualify.

### Mapbox map

Map opens directly to Singapore with a floating address search, filters, current-location action, matching service pins and a service preview. Address lookup uses OneMap; the native map and walking/driving routes use Mapbox. Public transport uses OneMap.

Tap the map search field to reveal saved services. Selecting one centres the map and opens its preview; saved services outside the current matches are labelled so they cannot be mistaken for dietary/filter matches.

Add `mapboxAccessToken=pk.YOUR_PUBLIC_TOKEN` to ignored `android/local.properties` (the key `mapbox.accessToken` is also supported), then rebuild. Alternatively set the `MAPBOX_ACCESS_TOKEN` environment variable. Use a public `pk.` token; never embed a secret `sk.` token. The default custom style is `android/app/src/main/assets/foodbridge-map.json`: muted land, pale blue water, soft green parks, road/transit labels, no unrelated POIs or 3D clutter. To use a published Studio style, add `mapboxStyleUri=mapbox://styles/YOUR_ACCOUNT/YOUR_STYLE_ID` to the same file. Mapbox logo/attribution and telemetry choices remain accessible. Setup follows [Mapbox's Android guide](https://docs.mapbox.com/android/maps/guides/install/).

The manually reviewed seed in `backend/seeds/verified-services.json` contains public contact/application facts from the [Willing Hearts provider page](https://willinghearts.org.sg/contact), with coordinates resolved through OneMap. It identifies a meal-delivery coordination site, not a confirmed walk-in collection point. Hours, diets, accessibility and live availability remain unknown. After starting the local stack, run `./tools/import-reviewed-seed.ps1` to import and publish it through the audited staff APIs. This is a small project seed, not a complete verified Singapore directory or a partner feed. The current workspace already has it published.

OneMap geocoding is attempted remotely with a local approved-address fallback. **Get directions** opens Map with the destination, route line, journey summary and optional route steps. Modes are **Walk, Public transport, Drive**. If no origin exists, enter a starting address on Map or use device location. Walking/driving use the configured public Mapbox token. Public transport requires `ONEMAP_EMAIL` and `ONEMAP_PASSWORD` in the API environment; set them before `./tools/start-local.ps1`, which forwards them into the API container. Without them, public transport shows Route unavailable; no invented transit route is returned. Routes and origin coordinates remain memory-only.

### FoodConnect provider directory

Find's **Browse food support providers** and Map's **Providers** action open a searchable directory with the FoodConnect categories, contact/application information and source notes. Provider contacts can exist without collection coordinates; no office is assumed to be a food pickup point. Provider-reviewed and directory-only facts are distinguished, with unknown suitability/stock retained. Support types have icon badges; expandable programme cards separate recipients, delivery methods, regions, dietary options and dates into labelled rows. Public directory GET `/api/providers` returns only active approved records; staff can review/edit/publish under **Providers**.

The reviewed seed is `backend/seeds/foodconnect-providers.json`; import it with `./tools/import-foodconnect.ps1` after starting the API. Coverage, verification and contradictions are documented in [docs/FOODCONNECT_IMPORT.md](docs/FOODCONNECT_IMPORT.md). The source is [FoodConnect](https://foodconnect.gov.sg/directory/), whose page footer dates its update to March 2023; listing facts should be confirmed with the provider. The local import does not imply live availability or a partner export.

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

For production deployment and outstanding evidence, read [docs/OPERATIONS.md](docs/OPERATIONS.md). This repository provides an application, not an already deployed public service. Monthly uptime and first-time user completion targets require real measurements.
