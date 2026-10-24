# Backend acceptance checks

Run from the repository root after installing `backend/requirements.txt` in `backend/.venv`:

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_accounts.py
./backend/.venv/Scripts/python.exe backend/tests/verify_flow_recovery.py
./backend/.venv/Scripts/python.exe backend/tests/verify_backend.py
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
./backend/.venv/Scripts/python.exe backend/tests/verify_routing.py
./backend/.venv/Scripts/python.exe backend/tests/verify_listing_review.py
```

These executable acceptance suites create disposable databases and exercise the API through FastAPI's TestClient. They do not write to the running application database. `verify_backend.py` checks discovery, moderation, imports, publication, backups and concurrent search performance; its results go to `docs/verification/`. `verify_staff_portal.py` checks administrator authorisation, report views, update validation, audit rollback, retries and operational status.

`verify_accounts.py` checks registration, email verification, password recovery, authenticated access, roles, lockout, session expiry/revocation and signed Google identity tokens. Mail and Google certificates are isolated fixtures; real mail/Google OAuth configuration is described in [Accounts](../../docs/setup/ACCOUNTS.md). Run the performance suite on its own to avoid contention from password-hashing checks.

`verify_flow_recovery.py` checks public privacy information and development-only test placeholders, safe field validation, preferred-time detail status and account-scoped report retry deduplication, including concurrent sends and replay after a target is retired. It uses a disposable SQLite database under `tmp/` and no live accounts or mail delivery.

`verify_routing.py` checks that directions use Google Routes, authenticated route access, the missing Google routing key message, safe upstream failure, and forwarding of all three modes and route geometry. It uses a disposable SQLite database and mocked successful routes; it does not verify live Google directions.

`verify_listing_review.py` exercises the 18 October research batch against a disposable SQLite database: reviewed updates, staged imports, publication audits, safe reruns, stale-edit and ID-collision rejection, retained directory facts and honest unknown availability. It never connects to the live database.

For PostgreSQL verification, set `TEST_DATABASE_URL` to an explicitly disposable test database. The test suite creates synthetic records; never point it at a real deployment.
