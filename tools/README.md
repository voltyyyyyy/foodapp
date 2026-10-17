# Repository tools

Run these commands from the repository root. Their paths stay stable for existing workflows.

| Command | Purpose |
| --- | --- |
| `./tools/start-local.ps1` | Build and run PostgreSQL, API, staff portal and scheduled maintenance |
| `./tools/setup-android.ps1` | Install the repo-local Android toolchain |
| `./tools/build-android.ps1` | Build the debug APK; accepts `-ApiUrl` |
| `./tools/import-reviewed-seed.ps1` | Audited import and publication of reviewed services; accepts `-SeedPath` |
| `./tools/import-foodconnect.ps1` | Audited import and publication of reviewed provider records |
| `./backend/.venv/Scripts/python.exe tools/apply-listing-review.py BATCH --snapshot SNAPSHOT` | Validate a sourced listing-review batch offline; `--apply` publishes through audited APIs using ignored staff credential files |
| `python tools/refresh_foodconnect_seed.py` | Reconstruct the provider seed from archived source material |
| `python tools/lab3/generate.py` | Rebuild and validate the separate Lab 3 report and TODO-only skeleton; `--verify` checks existing outputs |

Acceptance checks live in [backend/tests](../backend/tests/README.md). Downloaded SDKs, emulator files and screenshots live in ignored `tools/.local/`; local credentials and source captures live in ignored `backend/data/`. Neither belongs in Docker build contexts or source control.
