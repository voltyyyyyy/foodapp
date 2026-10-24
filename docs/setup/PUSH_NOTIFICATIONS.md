# Catalogue push setup

FoodBridge uses Firebase Cloud Messaging (FCM) to invalidate and refresh approved service/provider caches after an administrator publishes catalogue changes. Push messages contain only a change type, event reference and affected record identity/version; they do not include listing or account data. The Android client refreshes immediately while open and schedules a network-constrained WorkManager refresh while in the background.

## Firebase project

1. Create or select a Firebase project and add an Android app with package ID `sg.foodbridge.navigator`.
2. Enable Firebase Cloud Messaging for the project and download `google-services.json` into `android/app/`.
3. Keep the Firebase service-account key outside the repository and Docker build context. Grant its service account permission to send FCM messages.

The Android Gradle build applies the Google Services plugin only when `android/app/google-services.json` exists, so builds without FCM setup remain possible. FCM registration and push delivery stay disabled until the Android file and backend credentials are configured.

## Local API container

Set these environment variables before running `./tools/start-local.ps1`:

```powershell
$env:FIREBASE_PROJECT_ID = 'your-firebase-project-id'
$env:FIREBASE_SERVICE_ACCOUNT_FILE = 'C:\secure\foodbridge-firebase-admin.json'
./tools/start-local.ps1
```

The startup script mounts that file read-only at `/run/secrets/firebase-service-account.json` and enables push only when both the project ID and file are present. Never paste service-account JSON into a shell command or commit it.

For other deployments, set `PUSH_ENABLED=true`, `FIREBASE_PROJECT_ID`, and `GOOGLE_APPLICATION_CREDENTIALS` to the service-account file path inside the API container. Mount the credential through the platform's secret manager; do not bake it into the image.

For Docker Compose, set `FIREBASE_SERVICE_ACCOUNT_FILE` to the host-side credential file path. Compose mounts it read-only at the container path above. If no key is present, push delivery remains disabled even if the API is running.

## Delivery behavior

Signed-in Android installations register their FCM token at `PUT /api/push/device`; sign-out removes that device registration. The push is silent: Android refreshes both listing caches without showing a notification. A durable backend event is created in the same transaction as each successful published change. The dispatcher retries pending events and removes invalid device tokens. FCM delivery still depends on the device being online and able to receive messages; Android may defer normal-priority messages while sleeping, and the push expires after 24 hours. A later sign-in or manual refresh also fetches current data.
