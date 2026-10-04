# FoodBridge release operations

## Production configuration

Use one always-on API service and managed PostgreSQL in the same region, with HTTPS at the ingress and a durable database. Store credentials in deployment secrets. Disable demonstration fixtures (`DEMO_DATA=false`), provide permissioned verified data, provision a unique staff password and supply restricted Google Maps/Routes keys (docs/setup/GOOGLE_MAPS_SETUP.md). Set the release APK API URL to the production HTTPS endpoint. Sign the APK with an operator-owned key kept outside the repository. `compose.yaml` is a local PostgreSQL deployment recipe; production should prefer managed database backups and service monitoring.

The local JSON record store is organised by entity kind with explicit version and state columns. Before substantial production growth, migrate to dedicated organisation/service/report/observation tables with foreign keys and schema migrations; preserve all source and audit history. Production schema changes must be versioned and reviewed; `create_all` only creates the initial schema and does not migrate existing data.

## Maintenance and backups

Run `python -m app.maintenance` every hour for stored expiry and session cleanup. Reads also expire evidence immediately, preventing a delayed job from showing an obsolete available label.

The Compose maintenance service runs `python -m app.scheduler`, which waits for API readiness, expires evidence hourly, backs up immediately and every20 hours, retries failed backups every minute, and records backup health for staff under Operations. Use `BACKUP_PATH` to mount durable backup storage writable by the container user. External monitoring must alert on FAILED/OVERDUE states from the protected `/api/admin/operations` endpoint; log output alone is not an alarm delivery system.

Run `python -m app.maintenance --backup-dir /backups` at least daily, with pg_dump installed. Copy encrypted backup files to storage outside the database host. Alert if `last-success.json` is older than 24 hours; monitor failures immediately. A daily job alone does not establish the requirement until successful completions are recorded.

PostgreSQL restore: provision an empty database and run `pg_restore --clean --if-exists --dbname=<restore-database> <backup.dump>`. Verify approved records, reports and audit entries in an isolated deployment before using the restored database. Never test restore over the live database. Rehearse restore before release and record elapsed time and integrity checks.

## Availability and failure handling

Poll `/health/live` and `/health/ready` externally every minute. Record incident windows and announced maintenance separately. For each calendar month calculate `(observed_minutes - unplanned_unavailable_minutes) / observed_minutes`; the target is >=99%. Disable payload/query logging at ingress and APM so search origins cannot become persistent telemetry.

Search always uses the local approved dataset. A partner import or routing outage must not remove this dataset. Missing availability evidence remains UNKNOWN. Provider routing uses a bounded four-second deadline and returns an explicit unavailable response. Keep the most recent approved data on the device with an offline banner. Device coordinates are cleared when the application leaves the foreground and never enter DataStore, app backup or server records.

## Acceptance measurements

Record the APK build ID, device/OS, server size, dataset size, network conditions and raw timings. Suggested search baseline: 2,000 approved services, 20 concurrent callers, at least 1,000 measured requests. Cached/local dataset search API p95 must be <=500ms. Measure OneMap geocoding separately. For route UI latency, capture from tap to rendered route or unavailable message, at least 100 attempts during normal provider operation; p95 <=5 seconds. Emulator-to-localhost checks alone are not production evidence.

Recruit at least five first-time representative users. Ask each to search from a location with a dietary or distance filter without assistance. At least 80% must finish within two minutes. Record completion, time and assistance consistently; do not record precise location unnecessarily. Check TalkBack, font scaling, denied location permission, manual address recovery, no matches, stale observations, route failure and interruption on Android 12 and a later Android version.

## Remaining release evidence and inputs

Google Maps/Routes credentials, a sufficiently broad verified service dataset or authorised export, production host/TLS, scheduled off-host backup and monitoring, release signing key, production Android latency measurements and representative user trials are external inputs. A manually reviewed provider seed is supplied in `backend/seeds/verified-services.json`; it is not a comprehensive live directory. Source code and an APK cannot honestly prove 99% monthly uptime or user success. Keep this distinction in the course submission.

## Future surplus sharing

Treat sharing as a separate reviewed workflow. A listing timer does not establish safe food handling. Preserve preparation and storage information, allergens, consume-by times, donor provenance and moderation. Never display legal protection or certification badges without substantiation. The complete Lab directory has no reservation or food guarantee.
