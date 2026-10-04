# FoodBridge development handoff

Last updated: 3 October 2026, Singapore time. This file is authoritative for resuming work. Earlier chronological notes are in HANDOFF_HISTORY.md; their process IDs and pending actions are obsolete.

## User instructions

Review reference/ and SC2006 Labs.docx, resolve contradictions, choose the stack and plan the whole app. Build an opinionated, usable Android app meeting all functional and nonfunctional requirements. Install necessary tools. Keep this handoff updated in case usage runs out. User explicitly authorised a Luna subagent on 3 October to collect/verify FoodConnect directory information and add it to the app. No commits, pushes, public deployment or messages to others have been authorised.

## Current delivery

### Directions checkpoint

Latest user request: Get directions opens Map with the route; modes Walk/Public transport/Drive. Implemented routeDestination/routeMode/routeLoading in memory, Map route line/origin/destination/summary/steps, on-map start-address resolution, retry/close and mode changes. Directions opens Map immediately even without an origin, prompting starting address/device location. Cancels stale requests on edit/navigation; route/origin clear on background. Removed inline detail-map route result and Cycle option.

Walking/driving now use Mapbox Directions v5 directly with the already configured public token (never print URLs containing it). OneMap geocoding remains server-side; PT uses backend routeType=pt, Singapore departure date MM-DD-YYYY/time, TRANSIT and itinerary leg geometries/steps. Current local OneMap credentials are absent, so PT should show honest unavailable state until configured. tools/start-local.ps1 now forwards ONEMAP_EMAIL/ONEMAP_PASSWORD from environment without printing values. Privacy copy describes routing coordinates sent to Mapbox.

Backend image39b02d771607 rebuilt successfully. Final Android build51498 succeeded26s and installed/launched on emulator-5554. Mapbox asynchronous cameraForCoordinates now fits both route endpoints above the journey panel, with stale callback protection. Visual review: postal560123 to Krsna walking route117min/9.6km, driving25min/10.1km, public transport honest unavailable state without credentials. Screenshot tools/.local/screens/map-route-final.png; PT evidence map-transport-final.xml. No automated tests added or run.


### Current checkpoint - all support, directory schedules, actual listings only

Blank Find support is now enabled and opens All food support: all active provider contacts and service locations, alphabetically, with no nearest10 limit and no suitability filters applied. The page says this explicitly; saved preferences remain unchanged. Address searches still use nearest10 mandatory-filtered results; organisation names still open name matches. Returning from details preserves the all-support/name-match page.

All5 fictional services,5 fictional organisations and demo source (11 records) were withdrawn with WITHDRAW_DEMO audit entries by app.retire_demo. Actual records preserved:22 providers and2 service locations. Audit history remains available to staff. Demo seeding defaults false in API and tools/start-local.ps1. Android live/offline snapshot reads filter demo records so cached fixtures cannot reappear. Native header Demo action disappears when no fixtures are loaded. Do not re-enable DEMO_DATA or restore fictional fixtures.

Service.provider_id links Willing Hearts and Krsna to their corresponding FoodConnect provider records. Links added through2 versioned audited staff PATCH edits and retained in the reviewed seeds. DirectoryDistributionInfo now exposes programme frequency, explicit listed days/dates (including missing-data guidance), recipients, dietary options, delivery/collection, regions and estates. Both service and provider pages use it. No frequency is converted into invented current-date appointments. Service pages show useful directory facts first; calendar remains expandable when exact hours are unknown. Krsna directory weekdays refer to its Fresh Produce programme, not its meal-site hours.

Backend rebuilt image76ca44281d2e with demo seeding disabled and provider_id field. Retirement completed and API publication counts confirmed. Android build38710 succeeded26s; final build70314 succeeded23s with the offline-fixture safeguard and safe parallel fetch cancellation. Final APK installed/launched on emulator-5554. Visually reviewed blank Find support -> All food support (22 providers /2 service locations, no Demo header), and actual provider directory distribution cards. Screenshots: tools/.local/screens/all-support-real-only.png and distribution-directory-real.png. No automated tests added or run for these latest requests.

### Earlier refinement completed (historical evidence)

User requested prominent incorrect-information reporting, clearer result details and a calendar based on the reference screens; then reported search problems and requested organisation-name search. Implemented ServiceDetails.kt with icon/label sections, week calendar, date-specific overrides, overnight continuation, unknown-hours guidance, source evidence and prominent report buttons. Provider pages also link to report. Report target_kind now supports service/provider (default service; legacy service_id field preserved), protected moderation keeps version checks and validated publication. Back from report/confirmation returns to the original information page.

SearchSuggestions.kt matches imported provider and service/organisation names, with tappable suggestions on Find/Map and a separate name-results screen. Name matches are labelled as not suitability-filtered. Address search keeps top10 matching services; explicit Search this as an address avoids ambiguity. Fixed stale address candidate handling, empty-candidate errors, cancellation error handling and input-edit cancellation. Clarified no confirmed matches and added provider-contact fallback. Direct API reproduction: postal560123 resolves via OneMap; current Nut-free+Vegetarian filters return0; an unfiltered request returns7 sorted services (5 fictional,2 real). Saved preferences were not changed.

Backend rebuilt/restarted image87be286bff85 successfully; readiness and provider-report schema confirmed. Final Android build47370 succeeded45s and installed/launched on emulator-5554 (supersedes build62679/30s and all earlier builds). Emulator walkthrough confirmed name suggestions for will, opening Willing Hearts service, report navigation with the correct listing name, and back to the original service. Postal560123 search resolves to the actual Ang Mo Kio address and clearly shows0 confirmed matches under unchanged Nut-free+Vegetarian preferences. Service/details/report/calendar screenshots in tools/.local/screens/refined-*.png and address-search-confirmed.png. Initial calendar screenshot hid Sunday; final build fits all7 dates and exposes selected-date semantics. Final screenshot refined-calendar-final.png visually reviewed: all7 dates visible, fictional Harmony Saturday meal12:00?14:30 rendered from actual demo schedule. Organisation-name suggestion screenshot organisation-name-search.png captured on final build. No new automated tests created; direct search checks were explicitly requested by the user. Preserve current user preferences/bookmarks.


- Completed 3 October map work: map opens directly to native Mapbox with floating address search, filters, device-location search, Search this area, matching pins and a preview. SDK android-ndk27 11.32.0; custom style in android/app/src/main/assets/foodbridge-map.json. Mapbox public token configured in ignored android/local.properties under mapbox.accessToken (also accepts mapboxAccessToken/env MAPBOX_ACCESS_TOKEN). Never print it. Optional mapboxStyleUri supports a published Studio style. OneMap remains address/routing provider. Native logo/attribution preserved. Build45948 succeeded20s and the map style rendered in emulator; screenshot tools/.local/screens/mapbox-style.png. Fixed UTF8 BOM parsing. Scale bar suppressed to avoid miles/overlapping search. No new automated tests.
- FoodConnect integration: Luna collected all22 directory cards and provider sources, preparing stronger verification and one confirmed meal-service map seed. Parent added Provider domain model, audited import/publish/edit routes, public GET /api/providers, cached Android provider browser with category/name search, contacts/application and sources/notes. Provider entries support referrals/vouchers without inventing collection locations. Find and map link to browser. tools/import-foodconnect.ps1 imports and publishes reviewed records through staff API. Ran successfully: 22 providers published, with no quarantines or suspected duplicates; 18 provider_reviewed / 4 directory_only. See docs/FOODCONNECT_IMPORT.md. User asked verify information; source review must distinguish directory-only records from provider-reviewed facts and unknown stock.
- Earlier nearest10 milestone: backend image15199f583991 and Android build21721 (46 seconds) completed successfully. APK installed on emulator-5554 and launched. Find resolves an address, applies mandatory filters, sorts by unrounded Haversine distance then stable ID, and returns at most10 services. Default search area80km covers Singapore; narrower radii remain optional. Provider browser now uses support icons/badges and expandable programme cards with labelled recipients, delivery, regions, diets and dates. List/detail/programme screenshots visually reviewed in tools/.local/screens/provider-icons-*.png. No automated tests added or run.
- Map search saved-services dropdown remains implemented: tap the field to reveal saved services; selection centres the map and opens a preview. Saved pins outside matches are labelled suitability-not-checked. No search history persisted.
- All22 provider records are published, preserving public raw card/programme facts through22 audited PATCH edits. Realm contact-source URL and Krsna display encoding corrected through audited edits. Krsna confirmed map-site seed imported/published; hours, dietary suitability, accessibility and availability remain unknown. The current geocoded search dataset has2 actual services (Willing Hearts and Krsna); all5 demo services were withdrawn. Results can be fewer than10. Directory-only providers without verified service coordinates are not fabricated into map results.

- Design instruction: make the app cleaner, minimalist and smoother, using a frontend design skill. Installed/read `C:/Users/Mingchen/.codex/skills/frontend-design/SKILL.md` from anthropics/skills and applied it to native Compose. Design rationale/tokens in docs/DESIGN_REFINEMENT.md.
- Redesign implemented: smaller leaf/wordmark header; compact Demo data explanation; location-first home; summarized filters opening a native bottom sheet; stable-key lazy result list; lighter cards; bundled Atkinson Hyperlegible regular/bold (OFL in docs/licenses); contact-first details with expandable schedules/provenance; flat Settings; dropdown correction category and staff collection selector. All search criteria and source information remain reachable. Returning from Map to Find retains results.
- Earlier minimalist redesign build completed in34 seconds on3 October (exec88648); APK installed/launched successfully. Screenshots in ignored tools/.local/screens/redesign-*.png. Home, filters, three Halal search results and contact-first details visually reviewed. Final result-card metadata is tighter than the initial screenshot. The original Nut-free preference was restored after the walkthrough. No new automated tests or usability/performance measurements were run for this redesign.

- Native Kotlin/Compose/Material3 app, Android 12+ (minSdk31), compile/target35. Repo-local JDK17, SDK, Gradle8.13 and a portable Gradle wrapper are installed.
- FastAPI/Pydantic/SQLAlchemy backend. Complete local stack uses PostgreSQL17; Python-only alternative uses SQLite. Docker maintenance expires evidence hourly and backs up immediately/every20 hours, with retries and staff Operations health.
- Core workflows implemented: anonymous origin/filter search, ranked explanations, list/map parity, asynchronous ETA, details/contact, route/failure, local saves/preferences/cache, anonymous corrections/reference, authenticated staff moderation/edit/import/quarantine/duplicate review/audit/source health/operations.
- Search origins and staff tokens are memory-only. Origin clears on background; LocationManager requests are cancelled on background or after10 seconds. Approved snapshots, bookmark IDs and preferences persist locally.
- Availability and operating hours are separate. Expired evidence displays unknown; provenance, validity and confidence are retained. Unconfirmed dietary support never matches a mandatory diet.
- Debug APK: android/app/build/outputs/apk/debug/app-debug.apk. Uses http://10.0.2.2:8000 for emulator. Release requires HTTPS and operator signing; R8/resource shrinking configured, release APK not yet built/signed.
- docs/BUILD_PLAN.md contains source precedence, decisions, contradictions, architecture, implementation phases and requirement mapping. README.md provides commands; docs/OPERATIONS.md describes deployment and outstanding evidence.

## Source decisions

All DOCX text, 12 embedded diagrams/UI images, PRD, DESIGN.md and 11 HTML mocks reviewed. Original references untouched. Android user instruction overrides PWA wording. Anonymous seekers need no account; Profile becomes Preferences. UK/London/miles replaced by Singapore/Mapbox basemap/OneMap geocoding and routing/km. Surplus sharing, Google login, push/Singpass are outside numbered Lab directory scope. Unsupported safety/certification/legal guarantees removed. Dietary filters use AND. No fabricated ETA or stock assurance.

## Local environment and live services

Workspace C:/Users/Mingchen/foodapp, PowerShell. Docker Desktop runs:

- foodbridge-postgres, localhost5433, databasefoodbridge, volumefoodbridge-pg-data.
- foodbridge-api, localhost8000, latest backend imagefoodbridge-api:local.
- foodbridge-maintenance, volumefoodbridge-local-backups.
- Shared networkfoodbridge-development; task-owned containers restart unless stopped.

Run ./tools/start-local.ps1 to rebuild/restart the local stack while preserving database volumes. No uvicorn exec sessions remain active. Do not reuse old session IDs from history.

Staff email admin@foodbridge.local. Generated password only in ignored backend/data/local-admin-password.txt. PostgreSQL password only in ignored backend/data/local-postgres-password.txt. Never print, commit or expose them. Staff sign-in under Settings. Environment password changes do not reset existing accounts.

API31 emulator FoodBridge_API31 (Pixel5, WHPX), adb emulator-5554. At the user's request to view the app, the task's headless emulator was closed and restarted with a visible interactive window; FoodBridge launched. Inspect adb devices rather than relying on a PID. Visible emulator logs are ignored tools/.local/emulator-visible-*.log. SDK path in ignored android/local.properties. SDK repair completed; setup script uses native android.exe with an absolute SDK path and checks android.jar. Nonfatal SDK metadata version warning remains.

## Data

Five synthetic demo listings were withdrawn on3 October at the user's request; their organisations/source are also withdrawn. Only actual listings are public. Run docker exec foodbridge-api python -m app.retire_demo if resuming an older local database; this is idempotent and records an audit trail. A manually reviewed real provider seed now exists at backend/seeds/verified-services.json and has been imported/published to the local stack through staff APIs with an audit trail:

- Willing Hearts meal-delivery assistance; contact/application/address facts from https://willinghearts.org.sg/contact, reviewed 2026-10-01T16:02:49Z.
- Coordinates from OneMap geocode425792. Map pin is soup-kitchen/coordination site, not confirmed walk-in pickup. Application requires social worker/social agency. Hours/diets/accessibility/availability unknown; no stock observation invented.
- Recreate it with ./tools/import-reviewed-seed.ps1 after starting local stack. Script skips an already published seed. If partially imported, resolve pending/quarantine/duplicates in staff UI before retry; do not overwrite records directly.

FoodConnect supplies22 published provider contacts (18 provider_reviewed /4 directory_only). Authoritative seed: backend/seeds/foodconnect-providers.json; import with ./tools/import-foodconnect.ps1. Recreate Krsna with ./tools/import-reviewed-seed.ps1 -SeedPath backend/seeds/foodconnect-map-services.json. Provider-reviewed means relevant primary sources were reviewed, not that every directory fact is current. Broader confirmed service locations and suitability evidence remain needed for a useful public launch. Local demo fixture observations expire naturally.

## Evidence already collected

- Android debug compilation succeeded repeatedly, including location privacy fix, map sizing, native staff fields and nullable contact clearing.
- Android12 emulator launches successfully. Postal560123 geocodes via OneMap; Halal returns3 synthetic matches. Sticky Find CTA, compact results, availability timestamps, saved services/details/eligibility/travel caution work.
- Native Mapbox custom style, matching markers and attribution render. Legacy WebView evidence belongs to the earlier implementation. Map-data signature guard preserves pan/zoom during periodic expiry repaint.
- Staff login, services queue and native current/proposed editor opened. Missing routing credentials show honest Route unavailable while details remain. Native report end-to-end walkthrough remains unrecorded; backend submission/moderation is covered.
- docs/backend-verification.json:30 passing checks, SQLite cached dataset search p95=418.15ms.
- docs/backend-postgres-verification.json:30 passing checks, PostgreSQL p95=443.25ms. Both use2,000 approved synthetic services,20 concurrent callers,100 requests. These are local measurements, not production evidence.
- Actual pg_dump/pg_restore round trip into a fresh isolated database retained2,000 approved services and7 audit entries. Backup in ignored backend/data/postgres-verified-backup.dump.
- Maintenance logs confirm first scheduled pg_dump at2026-10-01T15:43:02Z; protected Operations showed HEALTHY. Fixed initial monotonic timer to negative infinity so fresh hosts back up immediately.

## Commands and next actions

1. Latest Android build70314 succeeded23 seconds and was installed/launched on emulator-5554. Backend image76ca44281d2e rebuilt/restarted with demo seeding disabled; all fictional records withdrawn. Current publication:22 providers,2 actual service locations. Blank Find support browses all; directory distribution details are linked to actual services. All three local containers and the visible emulator are running. Managed sandbox needed escalation to build/install/access Docker. Preserve the user's preferences and saved selections; do not assume earlier dietary choices remain current; the user actively changes preferences.
2. Run ./tools/build-android.ps1 to rebuild; optional -ApiUrl specifies emulator/device/API URL. Gradle wrapper also available under android/.
3. If asked to verify further, finish native report submission/reference and staff decision walkthrough, accessibility/font scaling/denied-permission checks, and a later Android version. Do not add/run tests unless user asks to test or verify.
4. Production release needs OneMap routing credentials, HTTPS host/API URL, signing key, broader reviewed data, off-host encrypted backups/alerts, representative user trial and deployed uptime/latency evidence. Do not claim all NFRs proven locally.
5. Keep this handoff and README current; never alter reference files. Avoid deleting task volumes or user files. Preserve ignored data/secrets.

Isolated backend verification requires a NEW database each run. Previously populated verification DB name is in ignored backend/data/postgres-verification-db.txt. Do not run fixture checks against the live database. No successful authenticated OneMap route has been observed because credentials are absent. Monthly99% uptime and80% of>=5 first-time users completing within2minutes require actual observation.

## FoodConnect source checkpoint completed - 3 October 2026

Official directory HTML exposes https://foodconnect.gov.sg/api/prod/foodProviders in __NEXT_DATA__.props.pageProps.url. The response contains22 records in one array. Ignored backend/data/foodconnect-sources/ stores source captures. tools/refresh_foodconnect_seed.py reconstructs public facts while preserving reviewed contacts. Internal PK/SK/agreement/download metadata and incidental person contacts are excluded. Ambiguous words such as Bi-monthly remain ambiguous. All source reconstruction, imports and provenance corrections are complete; do not repeat imports to overwrite records. backend/seeds/foodconnect-services.json is an old staging reference, not the authoritative directory seed.

Map saved-dropdown empty state was confirmed through UI XML. mapbox-saved-dropdown.png captured Settings after user navigation, so it is not dropdown screenshot evidence. Latest provider screenshots show the actual icon badges, contact hierarchy and programme sections. No new automated tests or performance measurements were run for these changes; earlier measurements do not prove current production NFRs.

