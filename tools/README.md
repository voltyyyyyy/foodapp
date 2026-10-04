# Repository tools

Run these commands from the repository root. Their paths stay stable for existing workflows.

| Command | Purpose |
| --- | --- |
| `./tools/start-local.ps1` | Build and run PostgreSQL, API, staff portal and scheduled maintenance |
| `./tools/setup-android.ps1` | Install the repo-local Android toolchain |
| `./tools/build-android.ps1` | Build the debug APK; accepts `-ApiUrl` |
| `./tools/import-reviewed-seed.ps1` | Audited import and publication of reviewed services; accepts `-SeedPath` |
| `./tools/import-foodconnect.ps1` | Audited import and publication of reviewed provider records |
| `python tools/refresh_foodconnect_seed.py` | Reconstruct the provider seed from archived source material |

Acceptance checks live in [backend/tests](../backend/tests/README.md). Downloaded SDKs, emulator files and screenshots live in ignored `tools/.local/`; local credentials and source captures live in ignored `backend/data/`. Neither belongs in Docker build contexts or source control.
