# Connect Google Maps

## 1. Create a Google Cloud project

Open https://console.cloud.google.com/ and create/select a project. Link a billing account. Enable **Maps SDK for Android** and **Routes API** under APIs & Services ? Library. Geocoding API and Places API are not required: address resolution continues through OneMap and organisation search uses our approved directory.

Google setup: https://developers.google.com/maps/documentation/android-sdk/get-api-key
Routes billing: https://developers.google.com/maps/documentation/routes/usage-and-billing

## 2. Create an Android map key

APIs & Services ? Credentials ? Create credentials ? API key ? Edit key:

- Application restrictions: **Android apps**.
- Package name: `sg.foodbridge.navigator`.
- Debug signing SHA-1: `FC:29:73:DB:AA:FE:89:CC:6B:5A:CE:64:EE:EA:B4:0B:6C:FF:87:4E`.
- API restrictions: **Restrict key**, select **Maps SDK for Android** only.

Append this line to `android/local.properties`, preserving existing SDK settings:

```properties
googleMaps.apiKey=YOUR_ANDROID_KEY
```

This file is ignored by Git. Do not use the server routing key here. A release build will need its release certificate fingerprint added separately. The map uses a bundled custom style; no Map ID or Cloud style setup is required.

## 3. Create a separate backend routing key

Create another API key. API restrictions: **Routes API** only. For production, application restrictions should be **IP addresses**, allowing the backend's public outbound IP. During local development use your public outbound IP if stable; localhost/10.0.2.2 are not Google's observed source IP. If local egress changes, update this restriction.

Save just the key, with no quotes, in:

```text
backend/data/google-routes-api-key.txt
```

This folder is ignored by Git. The start script loads it without printing it. Alternative: set `GOOGLE_ROUTES_API_KEY` in the launching shell. Never put this key in Android sources, local.properties, screenshots or chat.

## 4. Apply configuration

Tell Codex ?both Google keys are saved? to rebuild/restart/install. Or run from the project root:

```powershell
./tools/start-local.ps1
./tools/build-android.ps1
& ./tools/.local/sdk/platform-tools/adb.exe -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
& ./tools/.local/sdk/platform-tools/adb.exe -s emulator-5554 shell am start -n sg.foodbridge.navigator/.MainActivity
```

Key changes require rebuilding the APK; server key changes require restarting the API. The existing PostgreSQL volume and approved listings are preserved.

## Costs and release

Set Routes API daily quotas in Cloud Console and billing budget alerts. Budget alerts do not cap spend. Nearest-results ETA enrichment requests walking routes; these count toward route usage along with explicit directions.

Google route content displayed on a map uses Google Maps. Google agency links and walking warnings remain visible. Publish operator Terms of Use and Privacy Policy URLs before production release, referencing Google's terms/privacy requirements. The in-app privacy copy has been updated, but public operator pages are still a release input.

Policy: https://developers.google.com/maps/documentation/routes/policies
Security: https://developers.google.com/maps/api-security-best-practices

Until the keys are configured, the map shows a setup message and route requests show an explicit unavailable response. Live Google map/transit behaviour is not yet verified.
