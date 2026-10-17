"""Isolated acceptance probes; never write to the running application database."""
import json
import os
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from pathlib import Path
from statistics import quantiles

root = Path(__file__).resolve().parents[2]
temporary = tempfile.TemporaryDirectory(prefix='foodbridge-verify-')
verification_database = os.getenv('TEST_DATABASE_URL')
os.environ['DATABASE_URL'] = verification_database or ('sqlite:///' + (Path(temporary.name) / 'verification.db').as_posix())
os.environ['ADMIN_EMAIL'] = 'verify@example.com'
os.environ['ENVIRONMENT'] = 'development'
os.environ['ADMIN_PASSWORD'] = 'isolated-verification-only-2026'
os.environ['DEMO_DATA'] = 'true'
sys.path.insert(0, str(root / 'backend'))

from fastapi.testclient import TestClient
from app.main import app
from app.db import Record, Session, engine, iso, now
from app.maintenance import backup, expire
from sqlalchemy import select

checks = []
def check(name, condition):
    checks.append({'check': name, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(name)

with TestClient(app) as client:
    auth = client.post('/api/admin/login', json={'email': os.environ['ADMIN_EMAIL'], 'password': os.environ['ADMIN_PASSWORD']}).json()
    client.headers['Authorization'] = 'Bearer ' + auth['token']
    origin = {'latitude': 1.3691, 'longitude': 103.8494, 'radius_km': 5}
    response = client.post('/api/search', json=origin)
    check('approved local search', response.status_code == 200 and response.json()['total'] == 5)
    check('invalid Singapore origin', client.post('/api/search', json={**origin, 'latitude': 52}).status_code == 422)
    diets = client.post('/api/search', json={**origin, 'diets': ['Halal', 'Vegetarian']}).json()['services']
    check('all selected diets must match', len(diets) == 1 and diets[0]['id'] == 'harmony')
    check('no silent filter relaxation', client.post('/api/search', json={**origin, 'diets': ['Nut-free']}).json()['total'] == 0)
    check('anonymous admin denied', client.get('/api/admin/reports', headers={'Authorization': ''}).status_code == 401)
    check('blank correction rejected', client.post('/api/reports', json={'service_id': 'harmony', 'category': 'Hours', 'description': ' '}).status_code == 422)
    report = client.post('/api/reports', json={'service_id': 'harmony', 'category': 'Hours', 'description': 'Please confirm hours'}).json()
    check('unique pending correction reference', report['state'] == 'PENDING' and report['reference'].startswith('FB-'))
    auth = client.post('/api/admin/login', json={'email': 'verify@example.com', 'password': os.environ['ADMIN_PASSWORD']}).json()
    headers = {'Authorization': 'Bearer ' + auth['token']}
    queue = client.get('/api/admin/reports', headers=headers).json()
    check('pending moderation and current comparison', queue[0]['current']['name'] == 'Harmony Community Kitchen')
    decision_path = '/api/admin/reports/' + report['reference'] + '/decision'
    check('rejection reason required', client.post(decision_path, headers=headers, json={'decision': 'REJECTED'}).status_code == 422)
    approved = client.post(decision_path, headers=headers, json={'decision': 'APPROVED', 'target_version': 1, 'proposed': {'eligibility': 'Verified acceptance probe'}})
    check('approval publishes values', approved.status_code == 200 and client.get('/api/services/harmony').json()['eligibility'] == 'Verified acceptance probe')
    check('second decision blocked', client.post(decision_path, headers=headers, json={'decision': 'REJECTED', 'reason': 'already decided'}).status_code == 409)
    check('stale edit blocked', client.patch('/api/admin/services/harmony', headers=headers, json={'version': 1, 'proposed': {'name': 'stale'}}).status_code == 409)
    check('atomic audit recorded', any(a['target'] == report['reference'] for a in client.get('/api/admin/audit', headers=headers).json()))
    route = client.post('/api/routes', json={**origin, 'service_id': 'harmony'}).json()
    check('unconfigured routing gives honest failure', route['available'] is False)
    with Session.begin() as db:
        record = db.get(Record, 'northside')
        data = dict(record.data)
        observations = [dict(o) for o in data['observations']]
        observations[0].update(observed_at=iso(now()-timedelta(hours=3)), valid_until=iso(now()-timedelta(hours=1)))
        data['observations'] = observations
        record.data = data
    check('read-time expiry hides available stock', client.get('/api/services/northside').json()['availability'] == 'UNKNOWN')
    check('maintenance persists EXPIRED', expire() == 1)
    with Session() as db:
        check('original availability retained', db.get(Record, 'northside').data['observations'][0]['original_status'] == 'LIMITED')
    invalid = client.post('/api/admin/import', headers=headers, json={'source': 'Verified test export', 'permission': 'Synthetic acceptance data', 'records': [{'name': 'Missing fields'}]})
    check('invalid import quarantined', invalid.json()['quarantined'] == 1)
    check('quarantine stores reasons', bool(client.get('/api/admin/quarantine', headers=headers).json()[0]['reason']))
    check('source health available', bool(client.get('/api/admin/sources', headers=headers).json()))
    new_service = client.get('/api/services/harmony').json()
    new_service.update(id='imported-service', name='Verified Export Test Service', organisation_id='imported-org', latitude=1.41, longitude=103.91)
    imported = client.post('/api/admin/import', headers=headers, json={'source': 'Authorised synthetic export', 'permission': 'Acceptance fixture',
        'records': [{'id': 'imported-org', 'kind': 'organisation', 'name': 'Verified Export Test Organisation'}, new_service]}).json()
    check('organisation and service imports retain provenance', imported['accepted_pending'] == 2)
    check('pending imports remain private', client.get('/api/services/imported-service').status_code == 404)
    check('organisation publication', client.post('/api/admin/organisations/imported-org/publish', headers=headers).status_code == 200)
    check('service publication', client.post('/api/admin/services/imported-service/publish', headers=headers).status_code == 200)
    check('imported organisation displayed', client.get('/api/services/imported-service').json()['organisation_name'] == 'Verified Export Test Organisation')
    new_service.update(id='duplicate-service')
    duplicate = client.post('/api/admin/import', headers=headers, json={'source': 'Authorised synthetic export', 'permission': 'Acceptance fixture', 'records': [new_service]}).json()
    check('suspected duplicates flagged', duplicate['suspected_duplicates'] >= 1)
    duplicates = client.get('/api/admin/duplicates', headers=headers).json()
    candidate = next(d for d in duplicates if d['candidate_id'] == 'duplicate-service')
    check('duplicate prevents publication', client.post('/api/admin/services/duplicate-service/publish', headers=headers).status_code == 409)
    check('duplicate review decision', client.post('/api/admin/duplicates/' + candidate['id'] + '/resolve', headers=headers,
        json={'decision': 'DUPLICATE', 'reason': 'Confirmed synthetic duplicate'}).status_code == 200)
    backup_path = backup(Path(temporary.name) / 'backups')
    check('backup completes', backup_path.exists() and backup_path.stat().st_size > 0)
    # Larger geographically spread dataset; no real people or precise device origins are retained.
    with Session.begin() as db:
        original = dict(db.get(Record, 'harmony').data)
        for i in range(1994):
            data = {**original, 'name': f'Synthetic service {i}', 'latitude': 1.16 + (i % 100) * .003,
                'longitude': 103.62 + (i // 100) * .02}
            db.add(Record(id=f'load-{i}', kind='service', data=data))
    def timed(_):
        started = time.perf_counter()
        result = client.post('/api/search', json=origin)
        if result.status_code != 200: raise AssertionError('search failed under load')
        return (time.perf_counter() - started) * 1000
    with ThreadPoolExecutor(max_workers=20) as pool:
        durations = list(pool.map(timed, range(100)))
    p95 = quantiles(durations, n=100)[94]
    print(f'Local concurrent search p95: {p95:.2f} ms', flush=True)
    check('local 2000-service concurrent p95 <=500ms', p95 <= 500)
    results = {'checks': checks, 'search_local_p95_ms': round(p95, 2), 'requests': 100, 'concurrency': 20,
        'dataset_services': 2000, 'database': 'isolated PostgreSQL' if verification_database else 'isolated SQLite', 'production_evidence': False}
    report_name = 'backend-postgres-verification.json' if verification_database else 'backend-verification.json'
    (root / 'docs' / 'verification' / report_name).write_text(json.dumps(results, indent=2))
    print(json.dumps(results, indent=2))
engine.dispose()
temporary.cleanup()
