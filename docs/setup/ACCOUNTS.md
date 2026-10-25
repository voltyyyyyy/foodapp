# Accounts: make FoodBridge sign-in work

This guide explains how to run the sign-in features already implemented in this repository. It covers a local test account, real Google sign-in, email/password registration, verification, password recovery and staff access. Run PowerShell commands from the repository root unless a step says otherwise.

The revised SRS requires an active account for discovery, service details, routing, bookmarks and reports. Android provides the user experience; administrator moderation stays at `/staff/`.

## Choose your starting point

| Goal | Follow these sections |
| --- | --- |
| Sign in locally using a simple test email/password | [Prepare the stack](#1-prepare-the-local-stack), then [Create the local test user](#2-create-a-local-test-user), then [Build and install](#6-build-and-install-the-android-app) |
| Make the Google button work | Sections [3](#3-configure-real-google-sign-in), [5](#5-start-the-api-with-your-configuration), [6](#6-build-and-install-the-android-app) and [7](#7-confirm-that-the-account-flows-work) |
| Enable real email/password registration and recovery | Sections [4](#4-configure-verification-and-password-reset-email), [5](#5-start-the-api-with-your-configuration) and [7](#7-confirm-that-the-account-flows-work) |
| Keep an existing administrator's password while moving to email sign-in | Follow [legacy staff migration](#82-migrate-an-existing-user-id-without-changing-its-password) before configuring a bootstrap email |
| Diagnose an error | Use [Troubleshooting](#9-troubleshooting) |

The local test user works without SMTP or Google OAuth. Google-created users also do not need FoodBridge SMTP to sign in. New email/password registrations need a working mail service because those accounts cannot sign in until verified.

## 1. Prepare the local stack

### 1.1 Check the required tools

You need Docker Desktop, this checkout, the repository's Android toolchain, and an Android emulator or device. The project supports Android 12/API 31 and later. For Google sign-in, use a Google Play-enabled device/emulator with working Google Play services and a Google account available for the chooser. Follow Google's [Credential Manager implementation guide](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation) for platform prerequisites.

```powershell
Set-Location C:\Users\Mingchen\foodapp
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
Test-Path ./tools/.local/jdk/bin/java.exe
Test-Path ./tools/.local/gradle-8.13/bin/gradle.bat
Test-Path ./tools/.local/sdk/platform-tools/adb.exe
```

The three file checks should return `True`. If the Android tools are missing, follow [the root README](../../README.md) and run `./tools/setup-android.ps1`. Start Docker Desktop before building the local backend.

### 1.2 Rebuild the backend when adopting the account changes

An old running API does not gain the new authentication endpoints just because the source files changed. The following command rebuilds/recreates the local API and maintenance containers while retaining the PostgreSQL and backup volumes. It briefly interrupts the API; skip an unnecessary rebuild if the running image already contains the account implementation.

```powershell
./tools/start-local.ps1
Invoke-RestMethod 'http://localhost:8000/health/ready'
```

Readiness should return `status: ok`. Open `http://localhost:8000/docs` and look for the **Accounts** routes, including `/api/auth/login` and `/api/auth/register`. Open `http://localhost:8000/account/` to confirm that the email-link page is served.

If migrating a legacy staff User ID, read section 8.2 before setting `ADMIN_EMAIL`: setting it at startup can create the target account before migration copies the old verifier.

`tools/start-local.ps1` uses `ENVIRONMENT=development`, permitting the local HTTP emulator connection. The root Compose configuration uses production settings and HTTPS enforcement; it is a separate deployment path, not a drop-in replacement for this local walkthrough.

## 2. Create a local test user

### 2.1 Test credentials

Use this account only in your development installation:

| Field | Test value |
| --- | --- |
| Email | `seeker.test@example.com` |
| Password | `FoodBridge123!` |
| Display name | `FoodBridge Test User` |
| Role | `REGISTERED_USER` |
| State | `ACTIVE` |

These are deliberately public, synthetic test credentials. They are not an existing operator password or a production account. The password meets the 12-character minimum. The reserved example email is not a mailbox you can use for real verification or reset delivery.

**The account exists only after you run the creation command below.** Adding these values to this guide does not create it, and entering them into Register would leave a pending account waiting for an email that cannot be delivered to you.

### 2.2 Create the user in the development database

Run this after the updated local API is ready. The command uses the API container's configured database, refuses non-development environments, creates only a registered-user account, and never changes an existing account or its password. It does not change approved listings or create an administrator.

```powershell
$testUserSetup = @'
import os
from uuid import uuid4
from argon2 import PasswordHasher
from sqlalchemy import select
from app.db import Account, Session

if os.environ.get('ENVIRONMENT') != 'development':
    raise SystemExit('Refusing to create public test credentials outside development.')

email = 'seeker.test@example.com'
password = 'FoodBridge123!'
with Session.begin() as db:
    existing = db.scalar(select(Account).where(Account.email == email))
    if existing:
        raise SystemExit('This email already exists. Its state, role and password were not changed.')
    db.add(Account(
        id=str(uuid4()), email=email, display_name='FoodBridge Test User',
        password_hash=PasswordHasher().hash(password),
        role='REGISTERED_USER', state='ACTIVE'
    ))
print('Development test user created. Use the credentials in ACCOUNTS.md.')
'@
$testUserSetup | docker exec -i foodbridge-api python -
Remove-Variable testUserSetup
```

This is an explicit development fixture: it skips email verification for this single test user. Public registration still creates pending accounts and requires verification. Do not use the fixture command to activate real users.

If the email already exists, the command stops rather than resetting it. An account created previously through Register may still be pending; use a different reserved test email in the fixture and its credentials, or complete verification with an address you control. Do not delete the database or change application authentication rules to fix a test-account collision.

### 2.3 Sign in

Build/install the app using section 6. On the sign-in screen, enter the test email and password and press **Sign in**. You should reach Find support and see the test user's own name/email in **Profile**. An empty service directory is a separate data-import issue; it does not mean authentication failed.

This fixture lets you test discovery, local bookmarks, profile name changes and report submission. It does **not** validate email delivery or the real Google chooser. Changing its password replaces the documented password; it will not reset automatically on restart.

## 3. Configure real Google sign-in

### 3.1 Understand which credentials are needed

| Item | Where it belongs |
| --- | --- |
| Android OAuth client | Registered in Google Cloud with the Android package and signing SHA-1 |
| Web OAuth client ID | Same value in the backend's `GOOGLE_WEB_CLIENT_ID` and the Android build configuration |
| Web OAuth client secret | Not required by this ID-token flow; never put one in Android |
| Google Maps API key | Separate map configuration; it cannot replace an OAuth client ID |

The Android client identifies the installed app; the Web client ID is the audience requested by this implementation. Google's [Android sign-in codelab](https://codelabs.developers.google.com/sign-in-with-google-android) documents creating both clients in the same project.

### 3.2 Get the signing fingerprint from this machine

Use the actual certificate used to sign the APK you install. From the repository root:

```powershell
$env:JAVA_HOME = (Resolve-Path './tools/.local/jdk').Path
& ./tools/.local/gradle-8.13/bin/gradle.bat -p android --no-daemon :app:signingReport
```

Find the `debug` variant and copy its **SHA1** value. Do not copy a fingerprint from an old screenshot or another team member's machine. If the debug keystore has not been created yet, run `./tools/build-android.ps1` once, then repeat the report.

### 3.3 Create the Google project and clients

In [Google Cloud Console](https://console.cloud.google.com/), select one project for both clients. Under **Google Auth Platform** (also reached through **APIs & Services > OAuth consent screen**), complete Branding and Audience. For a development app with external users, configure the testing audience and add your intended test accounts where required by the console.

On **Clients**, create an **Android** client with:

- Package: `sg.foodbridge.navigator`.
- SHA-1: the debug fingerprint from section 3.2.

Then create a **Web application** client in that same project. Copy its client ID, which ends in `.apps.googleusercontent.com`. For this native Credential Manager ID-token flow, the Web client's browser origins and redirect URI fields can remain empty; `/account/` handles FoodBridge email links and is not a Google OAuth callback. These client setup details follow Google's [codelab](https://codelabs.developers.google.com/sign-in-with-google-android).

### 3.4 Configure the same Web ID in the API and APK

In the PowerShell window that will run startup/build commands:

```powershell
$env:GOOGLE_WEB_CLIENT_ID = Read-Host 'Paste the Google Web application client ID'
```

The Android build reads this environment variable first. Alternatively, add this property to ignored `android/local.properties`, preserving its existing SDK and map settings:

```properties
googleAuth.webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

`local.properties` configures Android only. The API still needs `GOOGLE_WEB_CLIENT_ID`. If both sources exist, the environment variable wins for the APK. A stale variable can therefore override a corrected properties file.

For repeatable local startup, save the public Web client ID as the sole line in ignored `backend/data/google-web-client-id.txt`. `tools/start-local.ps1` loads it when `GOOGLE_WEB_CLIENT_ID` is unset. Keep the matching `googleAuth.webClientId` in ignored `android/local.properties` for Android builds. The OAuth client secret is not used by this integration and must not be added to either file.

No Firebase project, `google-services.json`, additional sign-in framework or source-code credential edit is needed by the current implementation. Recreate the API container and rebuild/reinstall the APK after changing the client ID.

### 3.5 Test with a suitable account

Use a Google account permitted by your project's audience, with a verified email, and make it available on the test device. For the first Google test, use an email that has not already been registered with a FoodBridge password.

Google sign-in creates an active `REGISTERED_USER` account. Later Google logins use its stable Google subject identifier. A verified Gmail identity, or a verified Google Workspace identity whose `hd` claim matches its email domain, can reuse an existing registered-user account with the same email and no Google identity attached. This follows [Google's authoritative-email guidance](https://developers.google.com/identity/sign-in/android/backend-auth). Active accounts keep their ID, display name, password and password lockout state. An unfinished registration becomes active, but its unverified password, sessions and email links are removed and its name comes from Google. Google-created users can use password reset to set a FoodBridge password once SMTP is configured.

Third-party email addresses without a matching verified hosted domain still require the existing password/reset flow. Suspended accounts, administrator accounts and accounts attached to a different Google subject cannot be linked by email. Google failures are shown as general sign-in messages rather than errors on the manual email field.

The API obtains a one-use nonce, then validates the returned token using Google's authentication library before creating a FoodBridge session. See Google's [backend verification guidance](https://developers.google.com/identity/sign-in/android/backend-auth) for signature, issuer, audience and expiry validation. The Google token is not the bearer token used for subsequent FoodBridge API calls.

## 4. Configure verification and password-reset email

### 4.1 Choose an SMTP service

Use a mail service that permits authenticated SMTP submission and authorises the sender address. This backend supports username/password SMTP over STARTTLS or implicit TLS; it does not implement SMTP OAuth/XOAUTH2. If your provider permits only OAuth-based mail submission, choose a supported SMTP credential/relay or implement that separate adapter.

Set these variables in the same PowerShell window used to start the backend:

| Variable | Required value |
| --- | --- |
| `SMTP_HOST` | Your provider's SMTP submission hostname |
| `SMTP_PORT` | Usually `587` for STARTTLS or `465` for implicit TLS |
| `SMTP_SSL` | `false` for STARTTLS; `true` for implicit TLS |
| `SMTP_FROM` | A sender address authorised by the mail provider |
| `SMTP_USERNAME` | Provider-issued SMTP username, often the sender email |
| `SMTP_PASSWORD` | SMTP password or provider-issued app password |
| `AUTH_PUBLIC_URL` | The API/portal origin reachable from the browser opening the email |

```powershell
$env:SMTP_HOST = Read-Host 'SMTP host'
$env:SMTP_PORT = '587'
$env:SMTP_SSL = 'false'
$env:SMTP_USERNAME = Read-Host 'SMTP username'
$env:SMTP_FROM = Read-Host 'Authorised sender email'
$smtpCredential = Get-Credential -UserName $env:SMTP_USERNAME -Message 'Enter the SMTP/app password'
$env:SMTP_PASSWORD = $smtpCredential.GetNetworkCredential().Password
$env:AUTH_PUBLIC_URL = 'http://localhost:8000'
```

The credential prompt avoids putting the password itself into a command in shell history. The API/container still needs the credential in its configured runtime environment. Keep it out of source, documentation, screenshots and logs.

For a service using port 465, set **both** `SMTP_PORT='465'` and `SMTP_SSL='true'`. Leaving `SMTP_SSL=false` makes the backend attempt STARTTLS instead of implicit TLS. Unencrypted SMTP is not supported by this implementation.

### 4.2 Optional Gmail development sender

Where your Google account permits app passwords, enable 2-Step Verification, generate an app password for FoodBridge and enter that value in the credential prompt above. Availability depends on account and administrator policy; see Google's [app-password instructions](https://support.google.com/mail/answer/185833?hl=en).

For this sender, use `smtp.gmail.com`, port `587`, `SMTP_SSL=false`, and the complete sender address as both `SMTP_USERNAME` and `SMTP_FROM`. Google's [SMTP setup instructions](https://knowledge.workspace.google.com/admin/gmail/send-email-from-a-printer-scanner-or-app?hl=en) document the server and TLS ports. A regular Google account password is not the app password. The SMTP app password and Google OAuth client ID are separate credentials for separate integrations.

### 4.3 Make the link reachable

The backend appends `/account/` to `AUTH_PUBLIC_URL`, so provide an origin without an extra `/account` or `/api` suffix.

| Where the email link is opened | Example origin / condition |
| --- | --- |
| Browser on the development PC | `http://localhost:8000` |
| Browser inside the Android emulator | `http://10.0.2.2:8000` reaches the host's local API |
| Browser on a physical phone | A reachable HTTPS development/deployment origin |
| Production | Your real HTTPS application origin |

On a phone, `localhost` means the phone itself. Also, `tools/start-local.ps1` binds the host port to `127.0.0.1`, so replacing `localhost` with your PC's LAN address alone does not expose that server to a phone. Use a deliberate HTTPS deployment/forwarding setup for that case. For local verification, the simplest route is to open the email on the development PC, confirm the link there, then return to the Android app to sign in.

Email links are opened in a browser; this release does not deep-link verification/reset directly back into Android. The page removes the fragment from its address/history and asks for confirmation before consuming a verification link or accepting a new password.

## 5. Start the API with your configuration

### 5.1 Recreate the local container

After setting the variables for the integrations you want, run:

```powershell
./tools/start-local.ps1
Invoke-RestMethod 'http://localhost:8000/health/ready'
docker logs --tail 30 foodbridge-api
```

The startup helper passes the authentication/mail variables into the new API container. **`docker restart foodbridge-api` does not update container environment variables**; recreate it through the helper after changing them.

PowerShell `$env:` assignments affect that shell and its child processes. Reopening a terminal loses those assignments unless you load them again. The startup helper does not automatically load a root `.env` file. Compose reads `.env` for its own configuration, but Compose uses a different deployment path and production transport rules.

For a Google-only test, leave SMTP unset and use the Google button. For email registration/recovery, all applicable mail settings must be present. `DEMO_DATA=false` is retained by the local startup helper.

### 5.2 Check configuration without printing secrets

This prints only Boolean presence checks and compares the public Web client ID with the shell value:

```powershell
$configurationProbe = @'
import os
import sys
names = ['GOOGLE_WEB_CLIENT_ID', 'AUTH_PUBLIC_URL', 'SMTP_HOST', 'SMTP_PORT', 'SMTP_FROM', 'SMTP_USERNAME', 'SMTP_PASSWORD']
for name in names:
    print(f'{name}: {bool(os.environ.get(name))}')
supplied_id = sys.argv[1] if len(sys.argv) > 1 else ''
print('Google Web ID matches this shell:', bool(supplied_id) and os.environ.get('GOOGLE_WEB_CLIENT_ID') == supplied_id)
'@
$configurationProbe | docker exec -i foodbridge-api python - "$env:GOOGLE_WEB_CLIENT_ID"
Remove-Variable configurationProbe
```

The Google check should be `True` for Google sign-in. Required mail values should be present for your chosen SMTP service. A configured hostname alone does not prove authentication or delivery. Avoid unrestricted `docker inspect` environment output or `Get-ChildItem Env:` dumps when diagnosing credentials.

After the container has captured SMTP settings, you may remove `Env:SMTP_PASSWORD` and the `$smtpCredential` variable from the shell. You must supply the password again before a later container recreation. Keep `GOOGLE_WEB_CLIENT_ID` available for the Android build unless you intentionally use `local.properties`.

## 6. Build and install the Android app

### 6.1 Emulator

```powershell
./tools/build-android.ps1
& ./tools/.local/sdk/platform-tools/adb.exe devices
& ./tools/.local/sdk/platform-tools/adb.exe -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
& ./tools/.local/sdk/platform-tools/adb.exe -s emulator-5554 shell am start -n sg.foodbridge.navigator/.MainActivity
```

Replace `emulator-5554` if `adb devices` shows a different identifier. `install -r` updates the application without intentionally clearing its existing data. The normal build uses `http://10.0.2.2:8000` and package `sg.foodbridge.navigator`.

A Google Maps key is not required to test the login form; an unconfigured map is a separate feature configuration. See [Maps setup](GOOGLE_MAPS_SETUP.md) for that key.

### 6.2 Physical device over USB for local development

For one attached phone with USB debugging authorised:

```powershell
& ./tools/.local/sdk/platform-tools/adb.exe reverse tcp:8000 tcp:8000
./tools/build-android.ps1 -ApiUrl 'http://127.0.0.1:8000'
& ./tools/.local/sdk/platform-tools/adb.exe install -r android/app/build/outputs/apk/debug/app-debug.apk
```

With multiple devices, add the same `-s DEVICE_ID` selector to both ADB commands. The reverse mapping forwards the phone's port 8000 to the PC. It does not rewrite the hostnames in previously generated emails; choose `AUTH_PUBLIC_URL` for the browser that will actually open the links.

For a remotely hosted API, build with its HTTPS origin:

```powershell
./tools/build-android.ps1 -ApiUrl 'https://YOUR_API_HOST'
```

The helper builds a debug APK. Producing a distributable release additionally requires release signing and its matching Google Android OAuth certificate registration.

## 7. Confirm that the account flows work

### 7.1 Local fixture login

1. Enter `seeker.test@example.com` and `FoodBridge123!` after creating the fixture.
2. Confirm that Find support opens and Profile identifies **FoodBridge Test User**.
3. Sign out and confirm the app returns to sign-in without exposing Saved or Profile.

### 7.2 Real Google sign-in

1. Press **Sign in with Google** and select a permitted device account.
2. Complete the Google prompt.
3. Confirm that FoodBridge opens its protected home screen and Profile shows the selected identity.
4. Close/reopen the app and confirm that a valid FoodBridge session resumes.

The button existing on screen, a successful APK build, or a returned nonce alone is not proof of a completed Google login. The Google chooser and the API's token verification must both succeed.

### 7.3 Email/password registration

1. Use **Create account** with a real inbox you control, a display name of 1–100 trimmed characters, and matching passwords of 12–128 characters.
2. Check the inbox and spam folder for the verification message.
3. Open the link in a browser that can reach `AUTH_PUBLIC_URL` and press **Verify email**.
4. Return to Android and sign in using that email/password.
5. If the message is missing, use **Resend verification email**; only the replacement link remains valid.

New registrations are `PENDING_VERIFICATION` until the link is accepted. A verification link lasts 24 hours and can be used once. A delivery error after account creation may leave a pending account; retry verification instead of repeatedly attempting to register the same email.

### 7.4 Password recovery and change

From sign-in, choose **Forgot password**, submit a real registered inbox, open the newest reset email, and enter a valid new password and confirmation. Reset links expire after 30 minutes. Successful reset invalidates the account's existing sessions and reset links. Return to the app and sign in again.

Signed-in password users can instead use **Profile > Change password**, supplying the current password. A Google-created account without a FoodBridge password uses the reset-email action to set one. The example.com test fixture cannot receive that mail; use a real inbox to test delivery.

The recovery acknowledgement intentionally looks the same for eligible and unknown addresses. It is not confirmation that an account exists or that mail was delivered.

### 7.5 Account isolation and recovery

Save a service in account A, sign out, and sign in as account B. B should not see A's bookmark. Sign back in as A and confirm its bookmark remains. For a second development fixture, change the reserved email and display name in section 2's creation command; do not reuse A's account ID.

Also verify an interrupted report after session expiry: reauthenticate as the same account, confirm the draft remains, and submit only after reviewing it again. Five consecutive wrong password attempts block password sign-in for that account for 15 minutes, including attempts with the correct password during the block.

## 8. Configure staff email sign-in

The fixture in section 2 is a registered user. It is expected to receive an administrator-access error at `/staff/`. Google user registration does not create staff accounts.

### 8.1 Provision a new administrator

For a new local installation, set an email before startup:

```powershell
$env:ADMIN_EMAIL = Read-Host 'Administrator email address'
./tools/start-local.ps1
```

The helper uses the existing ignored `backend/data/local-staff-password.txt` or generates that local password file if missing. Its `ADMIN_PASSWORD` value comes from that file; setting a separate shell `ADMIN_PASSWORD` is not how this helper chooses the local password. Sign in at `http://localhost:8000/staff/` using the configured email and that local password.

Startup creates a missing administrator account; it does not reset or promote an existing account with that email. The helper records the configured email in ignored `backend/data/local-staff-email.txt` for future starts/import scripts.

For explicit provisioning or an intentional reset, use:

```powershell
docker exec -it foodbridge-api python -m app.manage_admin --email admin@example.org --display-name 'FoodBridge administrator'
```

Replace the example email and enter a 12–128 character password at the hidden prompt. Reusing an existing administrator email resets its password and revokes its sessions. It refuses to promote an existing registered-user account. If you choose a different password through this CLI, an older local password file is not automatically updated; keep your authorised local operator configuration consistent before using import helpers.

### 8.2 Migrate an existing User ID without changing its password

For an untouched legacy installation with no email account/file configured yet:

1. Keep `ADMIN_EMAIL` unset and do not create `local-staff-email.txt` before the first rebuild.
2. Run the updated startup helper so the container contains the migration CLI.
3. Run the migration command below.
4. Record the selected email for subsequent startup/import helpers.

```powershell
# Run this branch only before an email bootstrap account has been created.
Remove-Item Env:ADMIN_EMAIL -ErrorAction SilentlyContinue
./tools/start-local.ps1

$staffEmail = Read-Host 'Email to associate with the existing administrator'
$legacyUserId = (Get-Content -LiteralPath './backend/data/local-staff-user-id.txt' -Raw).Trim()
docker exec -it foodbridge-api python -m app.manage_admin --email $staffEmail --migrate-user-id $legacyUserId
if ($LASTEXITCODE -ne 0) { throw 'Migration failed; do not continue as if it succeeded.' }
$staffEmail.Trim().ToLowerInvariant() | Set-Content -LiteralPath './backend/data/local-staff-email.txt'
$env:ADMIN_EMAIL = $staffEmail.Trim().ToLowerInvariant()
```

The command copies the existing verifier into a new active administrator account, revokes legacy sessions and records an audit entry. It does not email the administrator or change the password. Legacy physical tables/columns are retained, but their old sessions are no longer accepted.

If `local-staff-email.txt` already exists, the startup helper reads it even when the shell variable is unset. Review whether the email account was already created before using this migration branch. Migration refuses to overwrite an existing email account. Use that account's existing credentials or an explicit authorised reset; do not remove users or database volumes to force migration through.

## 9. Troubleshooting

| Symptom | Check / action |
| --- | --- |
| `/api/auth/login` returns 404 or Accounts is missing from `/docs` | Rebuild the old API through the startup helper; restarting an old image is insufficient. Check the host/port in the APK. |
| Test email/password is rejected | Run section 2's fixture creation first. Check for a pending/different existing account. The command never resets it. Passwords are case-sensitive. |
| Google sign-in is not configured | Configure the Web ID on the API **and** in the APK build, then recreate/rebuild/reinstall both. |
| Chooser cannot start or reports no credential | Check Google Play services, an available Google account, audience restrictions, connectivity, package and actual signing SHA-1. A plain AOSP emulator is not a Google Play test environment. |
| Google token cannot be verified | The API and APK must use the same **Web** client ID, not the Android client ID or Maps key. Check clocks and API outbound connectivity to Google's certificates. |
| Google email already has an account | Verified Gmail/Workspace identities reuse eligible registered-user accounts. For third-party email, a different existing Google identity, or staff/inactive accounts, use the existing password/reset flow. Check that the API contains the current account-reuse implementation. |
| Account email is unavailable / 503 | Set the required SMTP and public-origin values in the API container. Production also requires an HTTPS `AUTH_PUBLIC_URL`. |
| Registration reports a mail-delivery failure | The account may already be pending. Fix sender authorisation, SMTP password/TLS/network settings, then resend verification. |
| Reset/resend acknowledges but no email arrives | Use an eligible account/inbox, check spam/provider delivery logs and mail configuration. The generic response intentionally does not disclose delivery/account state. |
| Verification/reset link is invalid or used | Use the newest link and request another if needed. Old links become invalid after replacement or successful use. |
| Link points to localhost on a phone | `localhost` is the phone. Correct `AUTH_PUBLIC_URL` for the receiving browser, recreate the API, and request a new email. |
| Authentication changes vanish after restarting | Reload shell variables before recreating the container. `local.properties` is not a backend env file; the helper does not read `.env`. |
| `HTTPS is required` / 426 locally | You are using production transport rules. Follow the local helper path for emulator HTTP or access a correctly configured HTTPS deployment. |
| Works in debug but not a distributed build | Register that APK's actual signing certificate. A Play-distributed app may use the Play App Signing certificate rather than the upload/debug certificate. |
| Password login fails after several attempts | Wait for the account's 15-minute lockout to end; repeated retries do not unlock it. |
| Staff email migration says the email exists | Bootstrap/registration may have created it already. Migration will not overwrite or promote an account. Review the selected identity and use authorised provisioning/reset. |
| Successful login but no listings | Check approved imports and filters. Seeker authentication does not seed or publish listings. |
| Browser email page works but Android cannot reach the API | Check the APK's API URL, emulator versus phone addressing, USB reverse mapping and API readiness. |

For local diagnostics, use `docker logs --tail 30 foodbridge-api` and `/health/ready`. Do not paste session tokens, verification/reset URLs, password files, complete container environments or full credential responses into shared logs/issues.

## 10. Production rollout and implementation reference

### 10.1 Production configuration

Use PostgreSQL and an HTTPS application origin. Set the Google Web ID and SMTP settings in deployment configuration/secrets, register the release Android certificate, and provision real administrators outside public registration. Configure Google audience/branding for the intended users before distributing the application.

The API rejects HTTP `/api/` requests in production. Terminate HTTPS at the ingress and set `FORWARDED_ALLOW_IPS` to trust only the actual reverse proxy so Uvicorn recognises its forwarded scheme. Keep the origin port private. Android release manifests forbid cleartext traffic.

The root [Compose file](../../compose.yaml) passes the auth/mail variables to its API service and uses `ENVIRONMENT=production`. It does not provision a TLS ingress, and its service/container names and volumes differ from `tools/start-local.ps1`. Preserve the database deployment you are actually using; do not switch stacks or remove volumes as an account-setup shortcut.

This release adds `accounts`, `account_sessions`, `account_links` and `google_challenges`. Startup creates missing tables without replacing listing/audit records or legacy administrator columns. Take the normal backup before rollout. Where the local maintenance container is running, its backup command is:

```powershell
docker exec foodbridge-maintenance python -m app.maintenance --backup-dir /backups
```

That path is backed by the maintenance container's backup volume; verify your deployment's backup/export arrangements separately. Old Android clients must be upgraded because discovery now requires bearer authentication.

### 10.2 Where the implementation lives

| File | Responsibility |
| --- | --- |
| [auth.py](../../backend/app/auth.py) | Registration, verification, passwords, Google token checks, sessions and account profile |
| [db.py](../../backend/app/db.py) | Account/session/link/challenge tables |
| [main.py](../../backend/app/main.py) | Account router, protected discovery/admin endpoints, HTTPS/validation handling |
| [AccountScreens.kt](../../android/app/src/main/java/sg/foodbridge/navigator/AccountScreens.kt) | Login, registration, recovery and profile UI |
| [AppViewModel.kt](../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt) | Credential Manager, auth state and draft recovery |
| [SessionStore.kt](../../android/app/src/main/java/sg/foodbridge/navigator/SessionStore.kt) | Android Keystore-backed session encryption |
| [Repository.kt](../../android/app/src/main/java/sg/foodbridge/navigator/Repository.kt) | Authenticated requests, local session deadlines and account-scoped bookmarks |
| [account page](../../staff-portal/account/index.html) / [script](../../staff-portal/account/account.js) | Browser verification/reset links |
| [manage_admin.py](../../backend/app/manage_admin.py) | Explicit administrator provisioning, resets and legacy migration |

### 10.3 Account API reference

These paths are already implemented; configuring the external services does not require inventing new endpoints. Request bodies use JSON, with bearer authentication for protected operations.

| Method / path | Purpose / principal fields |
| --- | --- |
| `POST /api/auth/register` | `display_name`, `email`, `password`, `confirmation` |
| `POST /api/auth/login` | `email`, `password`; returns FoodBridge session/account/expiry |
| `POST /api/auth/google/challenge` | Issues a short-lived one-use nonce |
| `POST /api/auth/google` | `id_token`, `nonce`; exchanges verified Google identity for a FoodBridge session |
| `POST /api/auth/verify` | `token` from a verification email |
| `POST /api/auth/resend-verification` | `email` |
| `POST /api/auth/forgot-password` | `email` |
| `POST /api/auth/reset-password` | `token`, `password`, `confirmation` |
| `GET /api/auth/me` | Current authenticated account |
| `PATCH /api/auth/me` | Own `display_name`; email/role cannot be changed here |
| `POST /api/auth/change-password` | `current_password`, `password`, `confirmation` |
| `POST /api/auth/logout` | Revokes the bearer session; idempotent for expired/revoked sessions |
| `POST /api/admin/login` | `email`, `password`; requires the administrator role |

The test fixture account is intentionally unsuitable for a real deployment. The source implementation and the remaining SRS relationships are documented below. Recorded acceptance/emulator evidence is in [verification/ACCOUNTS.md](../verification/ACCOUNTS.md); real Google consent and real mail delivery must be checked using your configured services.


## Session, privacy and local state

FoodBridge sessions expire on logout/revocation, after 30 minutes of inactivity, or 24 hours after sign-in. Five consecutive wrong password attempts lock that account's password sign-in for 15 minutes. A successful password sign-in resets the count; Google sign-in is a separate verified method and does not bypass password validation.

Android stores session credentials as AES-GCM ciphertext with an Android Keystore key, excludes app data from backups, and checks local expiry before accessing cached data or bookmarks. Foreground checks validate the session against the API; a 401 clears local credentials and hides protected content. Offline approved snapshots can be used only within the locally known session deadline. As with other offline clients, remote revocation can be learned only after connectivity returns.

Bookmarks and dietary preferences use keys scoped to the authenticated account ID. Signing out hides them; another account cannot see them through the app. Anonymous bookmarks from the old release are not assigned to an arbitrary account. Removed/unlisted bookmarks have an individual removal action. Cached approved service lists record the time of the actual successful local refresh.

Locations, route origins and results remain memory-only. Replacing/clearing the search, leaving the foreground, sign-out, and expiry clear the location. Account profiles, bookmarks and report metadata do not contain a device location. Reports store the server-derived reporter account ID.

The same account can recover an interrupted in-memory report or display-name draft after signing in again. Submissions require another explicit action. Another account receives a fresh screen. Password fields are never saved into Android instance state. Offline sign-out clears local access immediately and queues the server revocation encrypted for retry on the next sign-in/foreground check. Until the API can receive it, the old remote session remains subject to its normal deadlines.

The browser portal keeps tokens in memory, clears local sign-in state immediately on sign-out, and preserves record edit drafts during reauthentication by the same administrator. Failed sign-out revocations are retried when connectivity returns while that tab remains open. Closing or refreshing its tab requires another sign-in.

## Requirements and use-case relationships

| Requirement | Implementation / evidence |
| --- | --- |
| 4.4.1.1–4.4.1.5 | Authenticated reports, category/description validation, unique reference, server-derived reporter ID |
| 4.4.2.1–4.4.2.10 | Email login, active-state/role checks, revocation, access guards, screen recovery, inactivity/absolute deadlines, persistent lockout, operator-only administrator provisioning |
| 4.4.3.1–4.4.3.6 | Registration, validation, normalised unique email, one-use verification/resend and expiry |
| 4.4.4.1–4.4.4.4 | Generic recovery request, one-use reset, confirmation and global session revocation |
| 4.4.5.1–4.4.6.3 | Own profile, trimmed name editing, immutable email/role, validated password change and revocation |
| 4.1.6.1–4.1.6.7 | Account-separated device bookmarks, latest approved snapshots, empty state and removal of unlisted IDs |
| 4.1.1–4.1.5 | Existing mandatory-filter search, distance-first ordering with operating/freshness ties, result explanations, details and map markers; all now protected |
| 4.2.1–4.2.3 | OneMap routing adapter, map/distance/duration, explicit failure and approved contact actions |
| 4.3; 4.5 | Existing availability/provenance/import and audited administrator workflows retained, with role checks and revised sessions |
| 5.3.1–5.3.4 | Transient origins, production HTTPS enforcement, Argon2id passwords, Keystore-protected credentials |
| 5.1.1 | Disposable concurrent-search benchmark in `verify_backend.py`; a local result is not a production guarantee |
| 5.1.2; 5.2.1; 5.4.1; 5.6.1 | Route deadline and existing backup scheduler implemented; live route percentile, monthly availability, representative-user completion and production backup outcomes still need deployment/user-study evidence |

For the supplied use-case table, authentication is a **precondition**, not an unconditional `«include» UC-09`. None of UC-01–UC-10 invokes another complete supplied use case on every execution; their `Includes` cells can therefore remain **None**. The optional relationships can be recorded separately:

| Base use case | Optional follow-on use case / extension condition |
| --- | --- |
| UC-01 Find Food Support | UC-02 when a result is selected; UC-04 when Report is selected |
| UC-02 View Available Food Support | UC-03 when directions are requested; UC-04 when Report is selected |
| UC-05 View Saved Services | UC-02 when a saved service is selected; UC-01 when the empty-state Find action is selected |
| UC-07 View Status | UC-06 when the administrator chooses to edit the related record |
| Protected use cases | UC-09 is a conditional reauthentication flow after expiry; it does not submit the interrupted action |

If the course diagram uses `«extend»` for these optional behaviours, draw the arrow from the optional use case to its base and label the extension point. Do not add `«include»` simply to fill an empty table cell. UC-06's validation and atomic audit/publication are mandatory internal steps, not calls to UC-07's read-only report-view use case.

## Verify

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_accounts.py
./backend/.venv/Scripts/python.exe backend/tests/verify_backend.py
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
node --check staff-portal/portal.js
node --check staff-portal/account/account.js
./tools/build-android.ps1
git diff --check
```

The account suite uses a disposable database, captured mail and locally signed Google tokens with isolated certificates. It covers actual signature/audience/issuer/expiry checks without using real accounts. Configure real SMTP/Google OAuth to validate delivery and the native consent flow before release. Use an emulator/device to check keyboard scrolling, account switching, bookmark isolation, cancellation, and expiry recovery.


## Public account return and operator privacy configuration

Email verification and password reset finish on `/account/`, with a **Return to FoodBridge** link to `foodbridge://account/return`. This custom scheme returns to user sign-in, contains no credentials and never proves that email verification succeeded. The server remains authoritative. If Android does not open the link, users can launch FoodBridge from their app list; a computer user continues on their Android device. Custom schemes are not verified HTTPS App Links and another app can register the same scheme. A verified App Link requires a real controlled HTTPS domain and an `assetlinks.json` deployment; no such domain is assumed here.

`GET /api/privacy` and `/account/privacy.html` are available without a session, alongside signed-out Android Help and Privacy. Discovery, saves and reporting still require sign-in. Configure the API process with the actual operator's values:

| Environment variable | Meaning |
| --- | --- |
| `PRIVACY_CONTACT_EMAIL` | Monitored operator email for account access, correction, deletion and privacy questions. Must be a valid email address. |
| `PRIVACY_RETENTION_SUMMARY` | Operator-approved explanation of retention for accounts, reports/audits and backups, including how requests are handled. Up to 4,000 characters. |
| `PRIVACY_POLICY_URL` | Optional publicly reachable HTTPS privacy-policy page under the operator's control. |

The contact action opens a mail draft titled **FoodBridge account/data request**. It asks the user to identify their account and report references, never their password. Sending a request does not itself delete an account, and the application does not promise a review or deletion timeframe. The operator must monitor the mailbox, authenticate the requester safely, assess retained report/audit/backup records and carry out the applicable process. No automatic server-account deletion endpoint is claimed.

For development only, when all three privacy variables are absent, the API supplies clearly labelled **TEST PLACEHOLDER** values: `privacy-test@example.com`, `https://example.com/privacy` and a statement that retention is not established. `test_placeholder=true` and `configured=false` distinguish these examples from real operator information. The example mailbox is not a real FoodBridge request route; do not send personal data. Partially configured or malformed values are not silently replaced. Production never supplies these test defaults.

Missing or malformed production contact/policy configuration is shown as unavailable. Missing retention is described honestly instead of claiming a policy. **Supply and verify the real monitored contact and approved retention policy before production release.** The code cannot establish those organizational decisions. Set these variables on the API runtime/container; an environment value on the host alone is insufficient unless forwarded into the container. Do not publish private credentials as contact details.

Bookmarks/diets and reminders are separate from encrypted report drafts and receipts on the device. Removing local data does not delete the server account or submitted reports. Receipts contain reference, target/category and submission time; they are evidence of receipt, not a promise of assessment or a live status tracker. Reports sent by current clients include an account-scoped `submission_id` UUID: unchanged retries return the original reference/time even if a listing becomes unavailable. Editing the submitted content uses a new UUID. Legacy clients without the key retain the earlier behavior.
