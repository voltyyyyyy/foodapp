"""UC-06 / UC-07 acceptance checks against a disposable database, never live data."""
import os
import sys
import tempfile
import io
from pathlib import Path
from unittest.mock import patch
from datetime import timedelta

root = Path(__file__).resolve().parents[2]
test_root = root / 'backend' / 'data' / 'acceptance'
test_root.mkdir(parents=True, exist_ok=True)
temporary = tempfile.TemporaryDirectory(prefix='staff-', dir=test_root)
os.environ['DATABASE_URL'] = 'sqlite:///' + (Path(temporary.name) / 'test.db').as_posix()
os.environ['ADMIN_EMAIL'] = 'staff-test@example.com'
os.environ['ENVIRONMENT'] = 'development'
os.environ['ADMIN_PASSWORD'] = 'isolated-staff-acceptance-2026'
os.environ['DEMO_DATA'] = 'true'
sys.path.insert(0, str(root / 'backend'))

from fastapi.testclient import TestClient
from app.main import app
from app.manage_admin import main as provision_admin
from app.db import Session, Record, engine, iso, now
from sqlalchemy.exc import SQLAlchemyError

passed = []
def check(name, condition):
    assert condition, name
    passed.append(name)
    print('PASS:', name)

with TestClient(app, raise_server_exceptions=False) as client:
    portal = client.get('/staff/')
    check('deployable portal and restrictive browser headers', portal.status_code == 200 and
          "frame-ancestors 'none'" in portal.headers['content-security-policy'])
    for asset in ('portal.js', 'portal.css', 'atkinson.ttf'):
        check('packaged ' + asset, client.get('/staff/' + asset).status_code == 200)
    for path in ('reports', 'reports/missing', 'services/harmony', 'organisations/org-harmony', 'audit'):
        check('unauthorised read denied: ' + path, client.get('/api/admin/' + path).status_code == 401)
    check('unauthorised write denied', client.patch('/api/admin/services/harmony', json={
        'version': 1, 'proposed': {'name': 'Unauthorised'}}).status_code == 401)
    login = client.post('/api/admin/login', json={'email': os.environ['ADMIN_EMAIL'],
        'password': os.environ['ADMIN_PASSWORD']}).json()
    headers = {'Authorization': 'Bearer ' + login['token']}
    client.headers.update(headers)
    check('empty report queue', client.get('/api/admin/reports', headers=headers).json() == [])
    report = client.post('/api/reports', json={'service_id': 'harmony', 'category': 'Hours',
        'description': 'Please check the evening collection.', 'evidence': 'https://example.org/notice'}).json()
    report_path = '/api/admin/reports/' + report['reference']
    before = client.get(report_path, headers=headers).json()
    check('complete report details and related approved information', all(k in before for k in
        ('id', 'category', 'description', 'evidence', 'created_at', 'state', 'current')) and before['state'] == 'PENDING')
    initial_audit = client.get('/api/admin/audit', headers=headers).json()
    check('view report is read-only', client.get(report_path, headers=headers).json() == before and
        client.get('/api/admin/audit', headers=headers).json() == initial_audit)
    target = before['current']
    org_id = target['organisation_id']
    org_path = '/api/admin/organisations/' + org_id
    org = client.get(org_path, headers=headers).json()
    check('existing organisation and provenance displayed', org['kind'] == 'organisation' and org.get('source') and org.get('verified_at'))
    service_path = '/api/admin/services/harmony'
    body = {'version': target['version'], 'proposed': {'phone': 'bad-number'}}
    check('invalid input rejected', client.patch(service_path, headers=headers, json=body).status_code == 422)
    check('invalid save preserves approved record and audit', client.get(service_path, headers=headers).json() == target and
        client.get('/api/admin/audit', headers=headers).json() == initial_audit)
    body['proposed'] = {'delivery_information': 'Telephone the provider to arrange home delivery.',
        'diets': ['Vegetarian'], 'schedules': [{'days': [0, 2], 'start': '17:00', 'end': '19:00', 'label': 'Collection'}]}
    # Simulate a failure after the record UPDATE, when recording its audit entry.
    with patch('app.main.audit', side_effect=SQLAlchemyError('Simulated database failure')):
        check('database failure is reported', client.patch(service_path, headers=headers, json=body).status_code == 500)
    check('record and audit roll back together', client.get(service_path, headers=headers).json() == target and
        client.get('/api/admin/audit', headers=headers).json() == initial_audit)
    check('failed update can be retried', client.patch(service_path, headers=headers, json=body).status_code == 200)
    public = client.get('/api/services/harmony').json()
    check('revised approved values are public', public['delivery_information'] == body['proposed']['delivery_information'] and
        public['diets'] == ['Vegetarian'] and public['schedules'] == body['proposed']['schedules'])
    entries = client.get('/api/admin/audit', headers=headers).json()
    entry = next(a for a in entries if a['target'] == 'harmony')
    check('audit contains actor, action, target, timestamp and before/after', entry['actor'] == login['username'] and
        entry['action'] == 'UPDATE_AND_PUBLISH' and entry['timestamp'] and set(entry['detail']) == {'before', 'after'})
    check('stale save blocked', client.patch(service_path, headers=headers, json=body).status_code == 409)
    latest = client.get(report_path, headers=headers).json()
    check('report retrieves latest related values', latest['current']['version'] == target['version'] + 1 and
        latest['current']['delivery_information'] == body['proposed']['delivery_information'])
    check('blank organisation rejected', client.patch(org_path, headers=headers, json={
        'version': org['version'], 'proposed': {'name': ''}}).status_code == 422)
    check('organisation update publishes and audits', client.patch(org_path, headers=headers, json={
        'version': org['version'], 'proposed': {'name': 'Reviewed organisation name'}}).status_code == 200 and
        client.get('/api/services/harmony').json()['organisation_name'] == 'Reviewed organisation name')
    response = client.post(report_path + '/decision', headers=headers, json={'decision': 'APPROVED',
        'target_version': latest['current']['version'], 'proposed': {'eligibility': 'Contact us before collection'},
        'reason': 'Confirmed directly with the provider'})
    check('correction assessment publishes atomically', response.status_code == 200)
    assessed = client.get(report_path, headers=headers).json()
    check('previously assessed report remains viewable with decision metadata', assessed['state'] == 'APPROVED' and
        assessed['decision_actor'] == login['username'] and assessed['decided_at'] and assessed['reason'])
    with patch('app.main.serial', side_effect=SQLAlchemyError('Simulated retrieval failure')):
        check('retrieval failure returns error', client.get(report_path, headers=headers).status_code == 500)
    check('report retrieval can be retried', client.get(report_path, headers=headers).json() == assessed)
    with Session.begin() as db:
        db.add(Record(id='test-backup', kind='operation', data={'name': 'Backup', 'status': 'HEALTHY',
            'last_success': iso(now() - timedelta(hours=30))}))
    operation = client.get('/api/admin/operations/test-backup', headers=headers).json()
    check('operation details expose overdue backups', operation['status'] == 'OVERDUE')
    check('operation list and details agree', next(r for r in client.get('/api/admin/operations',
        headers=headers).json() if r['id'] == 'test-backup')['status'] == operation['status'])
    check('logout revokes session', client.post('/api/admin/logout', headers=headers).status_code == 200 and
        client.get(report_path, headers=headers).status_code == 401)
    with patch('sys.argv', ['manage_admin', '--email', 'test-user@example.com', '--password-stdin']), patch('sys.stdin', io.StringIO('first-acceptance-password\n')):
        provision_admin()
    user_auth = client.post('/api/admin/login', json={'email': 'TEST-USER@EXAMPLE.COM', 'password': 'first-acceptance-password'}).json()
    check('email administrator sign-in', user_auth.get('username') == 'test-user@example.com')
    user_headers = {'Authorization': 'Bearer ' + user_auth['token']}
    check('provisioned user is authorised', client.get('/api/admin/reports', headers=user_headers).status_code == 200)
    with patch('sys.argv', ['manage_admin', '--email', 'test-user@example.com', '--password-stdin']), patch('sys.stdin', io.StringIO('second-acceptance-password\n')):
        provision_admin()
    check('password reset revokes existing sessions', client.get('/api/admin/reports', headers=user_headers).status_code == 401)
    check('password reset replaces old credential', client.post('/api/admin/login', json={'email': 'test-user@example.com',
        'password': 'first-acceptance-password'}).status_code == 401 and client.post('/api/admin/login', json={'email': 'test-user@example.com', 'password': 'second-acceptance-password'}).status_code == 200)

engine.dispose()
temporary.cleanup()
print(f'{len(passed)} staff portal acceptance checks passed.')
