# Reverse-engineered architecture

Scope: working-tree implementation reviewed on 18 October 2026, based on commit `288ea9f` with pre-existing Android edits. This document records the current structure; it does not prescribe behaviour changes. The updated SRS was supplied in the conversation and includes UC-01 to UC-11.

## Runtime boundaries

Android Compose functions render `UiState` and call `AppViewModel`. The ViewModel calls `Repository` for HTTP requests, account-separated bookmarks and approved snapshots, and `ReminderStore` for local schedules and notifications. `SessionStore` encrypts credentials with an Android Keystore key. Device origins and routes are transient UI state.

FastAPI in `backend/app/main.py` mounts the staff and account web pages and includes the `auth.py` router. Account validation and administrator dependencies protect endpoints. `domain.py` validates support records and calculates hours, availability and geographic distance. SQLAlchemy sessions persist JSON-backed records and account tables. Publication and audit writes share a transaction. `onemap.py` supplies geocoding; the active route endpoint uses `google_routes.py`. Maintenance and backup scheduling remain separate module entry points.

The staff portal uses plain HTML, CSS and JavaScript served by FastAPI. It is a browser boundary, not a Kotlin class. Composable screens are functions, not an invented hierarchy of screen classes. Backend route handlers and publishing helpers are functions; there is no implemented `SearchController`, `ModerationService` or backend repository class.

## SRS-to-source mapping

| SRS concept / responsibility | Implemented location and representation |
| --- | --- |
| Search and criteria, UC-01 | `AppViewModel.kt`: `UiState` and `AppViewModel`; `domain.py`: `Search(Origin)`; `main.py`: `search()` |
| Recommendation and route | Result dictionaries / Android `Service(JSONObject)`; route JSON in `UiState`; `main.py`: `Route`, `route()`; `GoogleRoutes` adapter |
| Service details, UC-02 | `ServiceDetails.kt` composable functions; Kotlin `Service` in `Repository.kt`; Python `Service(Origin)` in `domain.py` |
| Organisation | `main.py`: `Organisation` request schema; persisted `Record(kind='organisation')`; service `organisation_id` is a logical JSON reference |
| Provider directory | `Providers.kt` functions and JSON; `domain.py`: `Provider`; persisted `Record(kind='provider')`; optional service `provider_id` |
| Operating hours / service sessions | `Schedule`, `Service.schedules`, dated `exceptions`, separate `distribution_schedules` and `distribution_exceptions`; no dedicated service-session SQL table |
| Availability observation | Embedded `Observation` Pydantic values in a service's JSON; expiry calculated for reads and applied by maintenance |
| Reports, UC-04 / UC-07 | `Report` schema, `Record(kind='report')`, reporter account ID in stored JSON; portal read and assessment workflows |
| Change request / update, UC-06 | `Change`, `Edit`, `Decision` request schemas in `main.py`; pending `Record(kind='change')`; `publish()` and `decide()`; no standalone `Update` ORM class |
| Account, UC-08 to UC-10 | `db.py`: `Account`; `auth.py`: registration, verification, password sign-in/reset, Google identity and profile functions |
| Authenticated session and links | SQL `AccountSession` and `AccountLink` with account foreign keys; one link table distinguishes verification/reset by `purpose`; `GoogleChallenge` is separate |
| Legacy administrator compatibility | `Admin.username` / `Token.username` retain physical `email` columns; current administrator authorisation uses the account role |
| Audit entry | `db.py`: `Audit`; logical actor/target values, not declared account/record foreign keys |
| Bookmark, UC-05 | Account-specific DataStore keys in `Repository`; not a backend `Bookmark` class or server table |
| Reminder settings/request, UC-11 | `Reminders.kt`: `ReminderStore` and `ReminderReceiver`, private `SessionWindow`; SharedPreferences, AlarmManager and NotificationManager; no server reminder table |
| Protected local session | `SessionStore.kt`: AES/GCM encrypted preferences with Keystore key; reminder owner is an association rather than an authenticated session |
| Import / quarantine / duplicates | `main.py` functions with `Record` kinds `source`, `quarantine`, `duplicate`; reviewed files under `backend/seeds/` |
| Expiry / backups / operational status | `maintenance.py`, `scheduler.py`; SQL records and backup files, not a queue or microservice framework |

`AndroidService` and `BackendService` in the diagram are disambiguated labels for the two real classes named `Service`. `Origin`, `Search`, `Schedule`, `Observation`, `Provider` and `Report` are Pydantic schemas; `Record`, `Account`, `AccountSession`, `AccountLink` and `Audit` are ORM classes. The diagram is intentionally selective; it omits legacy compatibility classes, request-only schemas and adapters to keep it legible.

## Differences to document, without changing implementation

| Supplied SRS statement | Observed implementation / limit |
| --- | --- |
| OneMap supplies routes and route-response target | Active route endpoint calls Google Routes; OneMap remains geocoding and an older adapter implementation. This requires SRS reconciliation, not an incidental code change. |
| Rank by match quality, distance, operating status and freshness | Backend sort tuple includes matched selected diets, unrounded distance, operating state, verification time and ID. All mandatory diets already pass, so the diet count is constant within a search; distance is effectively the primary varying criterion. |
| UC-06 stores a pending change and then approves it | The backend supports pending changes and decisions as well as direct administrator approve-and-publish edits with version checking and audit. A pending record is not mandatory on every implemented edit path. |
| Observation has a unique observation ID | The embedded `Observation` schema has status, source, observation/expiry times and optional confidence, but no explicit observation ID field. |
| Complete conceptual entity model | Several concepts are JSON structures, preferences or transient state rather than individual classes/tables. A one-file-per-SRS-noun skeleton would misrepresent this implementation. |

The archived build plan includes older anonymous-user and map decisions. Use current source and the supplied SRS to distinguish present implementation from history; do not copy those older claims into a current architecture report.

## Organisation decision

Retain the existing executable roots and stable tools. Put course-specific artefacts here, generation/validation tooling under `tools/lab3/`, and deliverables under `output/`. A later extraction of route handlers or UI state can be evaluated independently with behaviour tests. It is unnecessary for a TODO-only initialisation submission and would increase the risk of changing transaction, import, lifecycle or visibility behaviour during this task.
