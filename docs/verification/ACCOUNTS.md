# Account verification — 17 October 2026

The revised authentication implementation was checked with disposable SQLite databases and the Android API 31 emulator. The live FoodBridge database and its credentials were not modified.

| Check | Result |
| --- | --- |
| `backend/tests/verify_accounts.py` | 74 checks passed: registration, verification, resets, lockout, roles, session deadlines/revocation, profile protections, signed Google tokens and concurrent attempts |
| `backend/tests/verify_staff_portal.py` | 38 checks passed, including email provisioning and explicit legacy migration preserving the password verifier |
| `backend/tests/verify_backend.py` | 30 checks passed; authenticated search p95 387.30 ms over 100 requests, concurrency 20, 2,000 services |
| JavaScript and PowerShell | Syntax checks passed for both portal scripts and the affected startup/import scripts |
| Android | Normal debug APK built successfully for package `sg.foodbridge.navigator`, default emulator API `http://10.0.2.2:8000` |
| Source/docs | `git diff --check` and the changed guides' local-link checks passed |

Android UI checks used a separate `sg.foodbridge.navigator.qa` package and a separate API on loopback port 18765, with synthetic accounts and listings. Verified:

- Login screen layout and scrolling, with explicit feedback when Google OAuth is unconfigured.
- Email/password sign-in, service browsing, details and profile.
- Saving a service, signing out, and seeing **No saved services** in a second account.
- Returning to the first account and recovering its bookmark.
- Restoring the authenticated account after force-stopping/restarting the QA app.
- Revoking a QA session while a report draft was open, blocking the submission, returning to sign-in, and restoring the category/description after reauthentication as the same account.
- No report was stored by the interrupted request or automatically after sign-in; the synthetic QA database still contained zero reports.

The emulator checks caught and fixed Android 12 parsing of API timestamps containing `+00:00`. Session expiry now uses `OffsetDateTime` before converting to an instant.

The final login screen is recorded in [account-login.png](account-login.png). The standard debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

Live Google consent, real mail delivery, live OneMap routing, browser visual/keyboard checks and production PostgreSQL behaviour were not exercised in this run. Browser control was unavailable. Production availability, real route-response percentiles, representative-user completion and deployed backup outcomes require separate evidence. See [account setup](../setup/ACCOUNTS.md) before rolling out the new authenticated API or migrating staff access. The running user-facing Docker stack was not rebuilt as part of these isolated checks.

## Subsequent local activation — 17 October 2026

At the user's request, the local Docker stack was subsequently upgraded and the development account was created. These checks are separate from the isolated run above:

- Completed a PostgreSQL backup before the upgrade: `foodbridge-20261017T095750Z.dump` in the existing backup volume.
- Rebuilt the API and scheduled-maintenance containers using `tools/start-local.ps1`, retaining the database and backup volumes. Readiness passed and demo seeding remained disabled.
- Created the development-only `seeker.test@example.com` account documented in [account setup](../setup/ACCOUNTS.md), with an adaptive password hash, `ACTIVE` state and `REGISTERED_USER` role. Approved listings were not changed.
- Against the local PostgreSQL API, verified password sign-in, own-profile retrieval, authenticated service retrieval, denial of administrator reports to the test user, sign-out and rejection of the revoked session.
- Built and installed the standard Android debug APK on the API 31 emulator. Verified email/password sign-in, the test user's profile and session restoration after force-stopping and reopening the app.
- Reran `verify_accounts.py`: all 74 checks passed using its disposable database and simulated external integrations. This does not establish real Google consent or Gmail delivery.
- Retrieved the installed build's debug signing SHA-1: `FC:29:73:DB:AA:FE:89:CC:6B:5A:CE:64:EE:EA:B4:0B:6C:FF:87:4E`, for package `sg.foodbridge.navigator`.

The supplied Web OAuth client ID was subsequently configured in the local API and Android build, using ignored configuration files. The startup helper reloads the public ID on future container recreations. The API's configured audience and generated Android audience matched, readiness passed, and the real local API issued a Google challenge. The rebuilt APK installed successfully.

An attempted Google login on the API 31 emulator returned the app's Google-sign-in-unavailable error before consent. That emulator had zero Google accounts and Google Play services 22.50.14. Real consent and account creation remain unverified; complete them on a supported device/emulator with updated Google Play services and a signed-in Google account. The OAuth client secret is not used or stored by this configuration.

Gmail delivery awaits the sender address and a locally supplied app password. Staff email migration awaits the chosen full email address; existing staff passwords have not been reset. Browser control remained unavailable.

The checkout also contained ongoing browsing and reminder edits. The installed build allowed guest browsing at the time of these checks; authentication checks above do not claim that this build enforces the SRS sign-in requirement for every discovery screen. The earlier isolated checks and screenshot describe their respective builds.
