# Backend acceptance checks

Run from the repository root after installing `backend/requirements.txt` in `backend/.venv`:

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_backend.py
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
```

These executable acceptance suites create disposable databases and exercise the API through FastAPI's TestClient. They do not write to the running application database. `verify_backend.py` checks discovery, moderation, imports, publication, backups and concurrent search performance; its results go to `docs/verification/`. `verify_staff_portal.py` checks administrator authorisation, report views, update validation, audit rollback, retries and operational status.

For PostgreSQL verification, set `TEST_DATABASE_URL` to an explicitly disposable test database. The test suite creates synthetic records; never point it at a real deployment.
