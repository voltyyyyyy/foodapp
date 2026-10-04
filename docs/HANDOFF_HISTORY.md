# FoodBridge development handoff

Last updated: 1 October 2026, Singapore time. Update this file after each meaningful milestone and before ending a session.

## User instructions and authorisation

The user first asked to review the reference folder and SC2006 Labs.docx, choose the optimal stack, resolve contradictions and write a whole-app build plan. They then authorised opinionated design improvements and asked to build the best version. Final product must be an Android app satisfying every functional and nonfunctional Lab requirement. They explicitly authorised installing anything necessary. They requested this persistent handoff log in case usage runs out.

Continue implementation autonomously. No deployment/account credentials have been supplied. Do not claim production uptime or representative user success without measurements. Do not create fake verified service data. No subagents have been authorised; work directly.

## Decisions and source review

All eleven reference HTML exports, PRD, DESIGN.md and complete DOCX text were read. All twelve embedded images were inspected, including both use case diagrams, dialog maps and entity/boundary/control class diagram. DOCX images were extracted read-only to `%TEMP%/foodbridge-plan-review`. Original references remain unchanged.

`docs/BUILD_PLAN.md` contains the chosen stack, source precedence, contradiction table, domain/API decisions, design direction, implementation sequence and a requirement coverage table. User's Android requirement takes precedence over PWA wording in PRD. Stack: native Kotlin/Compose/Material3, Android12+ minSdk31, FastAPI/Pydantic/SQLAlchemy API, SQLite local and PostgreSQL production, DataStore local IDs/preferences/approved snapshots. OneMap routing/geocoding is server-side; a restricted Leaflet WebView renders maps. Public search, reporting and bookmarks are anonymous. Profile became Preferences. Surplus-sharing market is future scope, not a Lab requirement. UK/London/miles and unsupported safety guarantees removed.

## Files created

- `android/`: Gradle project, AGP8.13, Gradle8.13, Kotlin2.1.20, Compose BOM2025.04.01, SDK35. `MainActivity.kt` contains native seeker/staff screens, design tokens and native date/time pickers. `AppViewModel.kt` owns transient state, APIs, search, ETA enrichment, local saves and moderation. `Repository.kt` has OkHttp, DataStore and approved offline cache. `FoodMap.kt` renders OneMap tiles with attribution and native selection bridge. No device origin is persisted. App clears transient coordinates when leaving foreground. Release disallows HTTP; debug allows local HTTP.
- `backend/app/`: record persistence, validated schemas, schedule/freshness rules, OneMap adapter, fictional seeds, API and maintenance/backup command. Protected administrator sessions are expiring hashed random bearer tokens; password hashes use Argon2. Public reports are moderated. Approval/target update/audit use transactions and conditional version/state changes.
- API endpoints cover search, geocode, routes, approved details, reports, staff login/logout, queues, direct service edit, organisation creation, proposed changes, approve/reject, import/quarantine/duplicates, pending import publication, audit/source health and health checks.
- `tools/setup-android.ps1`: installs repo-local JDK17, Gradle8.13 and SDK (see installer issues below; needs repair).
- `tools/build-android.ps1`: assembles debug APK with optional `-ApiUrl`.
- `tools/verify-backend.py`: isolated acceptance probes and concurrent search timings, writes `docs/backend-verification.json` if successful.
- `README.md`, `docs/OPERATIONS.md`, `backend/Dockerfile`, `compose.yaml`, `.gitignore`.

## Installed environment and live processes

Workspace: `C:/Users/Mingchen/foodapp`, PowerShell. Node22.20, Python3.11, uv and DockerCLI were already available. Docker engine was not running. Android SDK/adb/Android Studio were absent initially. Repo-local Java17 and Gradle8.13 installed in ignored `tools/.local/`. Backend dependencies installed in `backend/.venv/` using uv.

API is running on host port8000 using uvicorn with access logging disabled. Exec session ID22174, process ID43548 at startup. Working directory is `backend`. Local generated staff password is in ignored `backend/data/local-admin-password.txt`; email `admin@foodbridge.local`. Never print or commit that password. This is a provisioned local account, not a public default. Backend process does not use reload: restart it after backend code changes to exercise new code.

Other process/session IDs at handoff creation:

- 27374: `backend/.venv/Scripts/python.exe tools/verify-backend.py`, running isolated checks. Poll for result. It uses a temporary DB and synthetic 2,000-service data, with100 requests and20 concurrent workers. No running-app database writes.
- 23857: direct platform35 archive download and Expand-Archive repair; currently running. Poll. Script downloads `https://dl.google.com/android/repository/platform-35_r02.zip` into `tools/.local/platform35.zip`, extracts into `tools/.local/platform-unpacked`, copies `android-35` into SDK platforms directory.
- 41578: first Gradle build FINISHED FAILED due incomplete platform35 (no android.jar), before Kotlin compilation.
- 5404: SDK setup FINISHED; incorrectly reports successful install even though latest sdkmanager shim split semicolon package names.
- 32819: emulator/API31 Google APIs x86_64 install FINISHED SUCCESSFULLY. Emulator434MB and system image1.4GB downloaded and unpacked.

Session IDs may expire across sessions. Inspect ports/processes/files rather than assuming they still exist. Never terminate unrelated user processes.

## SDK installer issue and recovery

Latest official command-line tools downloaded were revision23 (`commandlinetools-win-16111833_latest.zip`). `sdkmanager.bat` is now a deprecated wrapper around `android.exe` and splits semicolon package names on Windows; use native exe instead. New CLI supports `--no-metrics`.

Correct command:

```powershell
$sdkAbsolute = (Resolve-Path tools/.local/sdk).Path
& ./tools/.local/sdk/cmdline-tools/latest/bin/android.exe --sdk=$sdkAbsolute --no-metrics sdk install 'platforms;android-35' 'build-tools;35.0.0'
```

Relative `--sdk=./tools/.local/sdk` triggers an extraction SecurityException. Always use resolved absolute SDK. The failed attempt left `tools/.local/sdk/platforms/android-35/package.xml` with no android.jar. CLI then thought it installed, so a direct official archive repair is running. Build-tools35.0.0 and platform-tools installed correctly. API31 system image/emulator installed with absolute path correctly. `android/local.properties` has `sdk.dir=C\:/Users/Mingchen/foodapp/tools/.local/sdk`.

Repair setup script to use absolute SDK/native android.exe and verify android.jar exists. Consider pinning older command-line tools if latest XML metadata causes issues, but do not erase the working emulator/system image.

## Verification completed so far

Running backend smoke check: health ready, search returns five fictional services. Anonymous report accepted, appears in staff queue, rejected with reason, second decision returns409, audit exists. Local backup succeeded via `python -m app.maintenance --backup-dir data/backups` and is in ignored backend data. An integration report remains REJECTED in local DB with an explicit verification reason; no real records modified.

NOT yet claimed: successful Android compilation, installed APK, Android emulator UI, live successful OneMap routes, production PostgreSQL execution, production performance, 99% monthly availability or first-time user study. `docs/OPERATIONS.md` distinguishes implementation from production evidence.

## Immediate next actions

Milestone update: isolated backend acceptance checks now PASS (22 checks). The first performance run failed because `latest_update` materialised all 2,000 JSON records for every query. Fixed it to use SQL MAX(updated_at). Search also prefilters a latitude/longitude bounding box. Latest measured local p95 is324.27ms with2,000 synthetic services,20 concurrent workers and100 requests; results in `docs/backend-verification.json`. This is local evidence, not production proof. Verification process finished and engine was disposed before temporary cleanup. Backend live process still needs restart to pick up these improvements.

Android emulator milestone: AVD `FoodBridge_API31` created via avdmanager (pixel5 profile, Google APIs x86_64), acceleration WHPX usable. Started headless via hidden Start-Process, PID13672. `adb shell getprop sys.boot_completed` returned1. Emulator logs are in ignored `tools/.local/emulator-out.log` and `emulator-error.log`. AVD is under user's `.android/avd`.

UI improvements since log creation: compact ResultsScreen replaces the full search form after submission; edits preserve inputs; date/time now native pickers; fresh details preserve match explanations/distance; offline snapshots recalculate schedules and evidence expiry; created organisation ID and import counts are shown in staff notices.

Added production maintenance service in Compose: `backend/app/scheduler.py` waits for API readiness, expiry hourly, backups immediately/every20 hours with minute retries and protected Operations health. Staff UI has Operations tab. Off-host encrypted storage and external alert delivery still require production configuration. SDK repair completed enough for Gradle resources/manifest tasks. First real build failed on conflicting cleartext manifest attributes; debug manifest now has `tools:replace`. Build session32051 is compiling next.

APK milestone: build32051 SUCCEEDED, compile and assembleDebug complete (1m39s). Debug APK exists at `android/app/build/outputs/apk/debug/app-debug.apk`. Warnings: deprecated non-AutoMirrored directional icons and nullable `opt(detail).toString()`; fix when editing those sections. Android SDK metadata version warning is nonfatal. APK install/launch and screenshot capture now in progress. Latest Operations UI edit occurred after successful build; rebuild before final delivery. SDK setup script now uses native android.exe absolute SDK path and checks android.jar.

Runtime milestone: Android12 emulator installed/ran app successfully, no AndroidRuntime crash. Screenshots `tools/.local/screens/home.png` and `results.png`. Postal560123 geocoded with real OneMap to123 Ang Mo Kio Ave6; Halal filter returned3 services and compact results. Screenshot review found Android12 ISO Instant parser failed on Python offset timestamps, incorrectly showing UNKNOWN and Not confirmed. Fixed parsing to OffsetDateTime.toInstant for evidence and display. Moved Find CTA to sticky bottom panel, shortened origin label, added last-local-update UI. Server now attaches canonical organisation_name separately. Rebuild3668 and wrapper-generation19529 were launched. Additional ViewModel clock/cancellation edits happened afterward; compile again before final install.

Live API restarted after initial performance changes: session38512, PID27612. It still needs another restart for organisation-name API changes. Original22174 ended. Platformrepair sessions20704 and23857 completed successfully. Backend verification still at22passingchecks/324.27ms; rerun after API changes when needed. Gradle wrapper generation should make Android project portable with `android/gradlew.bat`.

Latest milestone: build50412 SUCCESSFUL (37s) includes sticky Find CTA, OffsetDateTime fix, periodic expiry repaint, cancellation when leaving app, Operations staff UI, native staff contact/location/type/evidence fields with advanced record editor. Gradle wrapper files generated successfully. Latest backend checks still22PASS with p95=353.54ms after organisation-name enrichment. API restarted again: session30783 PID23692 with latest backend code. Installing latest APK and continuing emulator flow. No production credentials or dataset supplied.

Emulator evidence after latest install: postal560123 + persisted Halal preference returns3 matching services. Availability now correctly displays Limited availability and Checked1Oct11:01PM; parser fix verified. Saved Northside via bookmark and opened detail; eligibility/diet/wheelchair/match explanations and contact-before-travel warning rendered. Sticky CTA visible on initial screen without scrolling. Latest screenshots in ignored tools/.local/screens. Staff editor phone/website clearing fixed to JSONObject.NULL after build; next final build must include it. Import ID validation and atomic pending-import publication added to API after live restart; restart30783 before final verification.

More runtime evidence: Get directions returned honest Route unavailable while service detail stayed visible. Saved tab displayed the bookmarked Northside service without sign-in. Docker Desktop started hidden for PostgreSQL verification; Docker engine now responds (29.6.2). Creating task-owned postgres17 container `foodbridge-postgres`, port127.0.0.1:5433, volume `foodbridge-pg-data`; random password stored only in ignored `backend/data/local-postgres-password.txt`. Do not expose it. New organisation import/publication/edit paths and staff organisation tab added; backend acceptance script expanded to cover pending visibility, imported organisation names and duplicate review. Rerun after these changes. Demo banner now visible across app to identify fictional fixtures prominently.

Latest checks:30PASS, SQLite2,000services/20concurrent/100requests p95=418.15ms while emulator/Docker are active; `docs/backend-verification.json` updated. Container `foodbridge-postgres` created successfully. Backend image build session91183 running; final Android build session14669 running. Dockerfile was changed afterward to create /backups with correct ownership, so rebuild image before Compose deployment if needed. Backend .dockerignore excludes secrets/data/venv. Default backups now use named writable volume, configurable with BACKUP_PATH.

Build14669 completed SUCCESSFULLY (30s), latest APK includes organisation screens, top demo banner and proper nullable-contact clearing. Image91183 completed SUCCESSFULLY; it predates /backups ownership and rate-salt edits but verification mounts latest source. Isolated PostgreSQL verification database name in ignored `backend/data/postgres-verification-db.txt`; runs inside API image with mounted workspace and TEST_DATABASE_URL, emits separate `docs/backend-postgres-verification.json`. Do not rerun on populated verification database; create a new isolated DB each run. Test script overwrites only its dedicated verification data, never production. Latest APK installed on API31 emulator.

PostgreSQL milestone:30checks PASS; p95=443.25ms,2,000services,20concurrent,100requests. Separate PostgreSQL report saved. Successful pg_dump/pg_restore round trip into a fresh task-owned restore DB returned2,000approvedservices and7auditentries. Retained ignored backup `backend/data/postgres-verified-backup.dump`. Restore DB name began foodbridge_restore_; list via psql if needed.

Local stack milestone: added `tools/start-local.ps1`, run SUCCESSFULLY. Latest image rebuilt. API30783 stopped; live API now Docker container `foodbridge-api` on127.0.0.1:8000 using PostgreSQL database `foodbridge`, with5fictional demo fixtures. Existing SQLite data preserved in backend/data but no longer live. `foodbridge-maintenance` runs scheduled expiry/backup, `foodbridge-postgres` persists on volume foodbridge-pg-data, and backups persist on foodbridge-local-backups. All task-owned containers restart unless stopped and share foodbridge-development network. Secrets same ignored local files. README updated with one-command local stack. No public deployment occurred.

Worker verification found initial monotonic timer0 deferred first backup on a newly booted host. Fixed to negative infinity so expiry and backup run immediately. Rebuilt/restarted local stack. Worker logs confirm expiry and pg_dump completed2026-10-01T15:43:02Z. Protected Operations endpoint reports HEALTHY with last-success timestamp. Startup script network attachment is now idempotent.

Map runtime review exposed a zero CSS map-height issue despite WebView native bounds. Leaflet loaded with markers/attribution but content height0. Fixed WebView MATCH_PARENT params plus explicit CSS viewport height and invalidateSize from native measured height. Android rebuild now running; verify map screenshot after install. This is the main current remaining runtime fix. Staff native flow and report form still need final UI walkthrough. Backend full checks already pass.

Map-sizing rebuild24676 SUCCEEDED(23s) and was installed. Further privacy review found pending LocationManager callbacks could arrive after onStop. MainActivity now owns/cancels the location request on background and after10s, preventing delayed retention and providing manual-entry recovery. Release build now enables R8/resource shrinking and keeps JavascriptInterface methods; debug APK remains readily inspectable. Inactive-service detail warns not to travel. Final debug build33752 running for those edits. Continue native map/staff/report walkthrough before final handoff.

Map runtime now VERIFIED: real OneMap Singapore tiles,3matching markers and OneMap/SLA attribution rendered correctly after viewport fix. Screenshot tools/.local/screens/map.png. Final build33752 SUCCEEDED(39s), includes location callback cancellation/deadline and release shrinking. Currently walking through staff login on emulator before installing that latest build for final handoff. APK is about61MB in debug (extended icon library); release R8 strips unused icons/resources.

Platform archive download completed (`tools/.local/platform35.zip`,64,273,788 bytes). Original Expand-Archive repair session23857 was still slow. A second repair session20704 uses explicit .NET zip extraction with path-boundary checks into `tools/.local/sdk/platforms/android-35`. Check its completion, then rebuild. It extracts only official platform content. Latest build has been launched again; do not assume success until log/APK checked.

1. Poll backend verification27374 and platform repair23857; fix any failures. Performance probes currently may fail if full-dataset work exceeds500ms. Search was recently changed to SQL JSON lat/lon bounding-box candidate filtering before Haversine; rerun only after changes justify it.
2. Build via `./tools/build-android.ps1`. Resolve Kotlin/compiler issues and repeat until APK exists. Expected APK `android/app/build/outputs/apk/debug/app-debug.apk`.
3. Create an API31 AVD with the installed native AndroidCLI or avdmanager; inspect help. Run headless with repo-local emulator and a hidden process window. Check acceleration support first. Boot, adb install APK, launch, inspect screenshots via adb and view_image. Use purpose-built adb CLI for device work. Native computer-use surfaces are disabled here.
4. Verify actual seeker flow with560123, filters/no-results, maps, bookmarks, details, report, staff login and approval; font scaling and permissions if emulator runs. Improve visual issues found.
5. Close requirement gaps: richer validated record editing, pending imported record publication, reliable source last-update display and last-success freshness, organisation-name display separate from service name, reusable source failure records, truthful states and conflicts.
6. Production operational code should include scheduled backup/age monitoring and deployment instructions. Credentials, verified dataset, host/signing are still external inputs; clearly state what is pending in final status rather than claiming all NFRs verified.
7. Update this log with every milestone, errors and next command. Keep README and actual implementation consistent. Do not commit/push unless requested. Original reference folder and ZIP were already untracked; preserve them.

## Important code review items

- `MainActivity.kt` uses Material icons from extended library; compilation may expose unavailable icon names or Compose signatures.
- `AppViewModel.refresh` initially attempted suspension inside state-update lambda; fixed by reading saves/diets before update.
- Staff fields combine JSON with named field overrides. For organisation changes there are fewer fields. Source record import requires a pre-existing approved organisation; UI creation returns only a notice, so expose created ID to user in a usability fix.
- Service `active` flag added to schema and search filtering. Approved list/details may need matching policy for inactive providers.
- DataStore cache persists only approved service snapshots, never origin or search criteria coordinates. Saved services need fresh operating status recalculation in offline mode as well as evidence expiry.
- Root `data/` may have been created by isolated script import, because db.py makes a relative data directory. Remove only task-created empty directory if appropriate, or ignore it. Verify resolved target before any recursive removal on Windows.
- Maps use Leaflet1.9.4 from unpkg and OneMap tiles; preserve attribution, avoid leaking staff token into WebView and validate service coordinate data. Native map fallback copy exists; offline maps are not promised.
