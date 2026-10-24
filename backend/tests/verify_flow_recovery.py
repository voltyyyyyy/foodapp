"""Isolated acceptance checks for readable errors, operator policy and safe report retries."""
import os
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

test_root = Path(__file__).resolve().parents[2] / 'tmp'
test_root.mkdir(exist_ok=True)
temporary = tempfile.TemporaryDirectory(prefix='foodbridge-flow-recovery-', dir=test_root)
os.environ.update(DATABASE_URL='sqlite:///' + (Path(temporary.name) / 'recovery.db').as_posix(),
                  ENVIRONMENT='development', DEMO_DATA='true')
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fastapi.testclient import TestClient
from sqlalchemy import select
from app.main import app
from app import auth
from app.db import Account, Record, Session, engine

passed = []
def check(name, condition):
    assert condition, name
    passed.append(name)
    print('PASS:', name)

try:
    with TestClient(app) as client:
        with patch.dict(os.environ, {'PRIVACY_CONTACT_EMAIL':'', 'PRIVACY_RETENTION_SUMMARY':'', 'PRIVACY_POLICY_URL':''}):
            privacy = client.get('/api/privacy')
            check('development defaults are explicitly test placeholders', privacy.status_code == 200 and privacy.json()['test_placeholder'] and privacy.json()['contact_email'] == 'privacy-test@example.com' and 'TEST PLACEHOLDER' in privacy.json()['retention_summary'] and not privacy.json()['configured'])
            with patch.dict(os.environ, {'ENVIRONMENT':'production'}):
                production = client.get('https://testserver/api/privacy').json()
                check('production never substitutes test policy or contact', not production['test_placeholder'] and production['contact_email'] is None and production['policy_url'] is None and production['retention_summary'] is None and not production['configured'])
        with patch.dict(os.environ, {'PRIVACY_CONTACT_EMAIL':'operator@example.org', 'PRIVACY_RETENTION_SUMMARY':'Isolated test policy', 'PRIVACY_POLICY_URL':'https://operator.example.org/privacy'}):
            policy = client.get('/api/privacy').json()
            check('actual configured policy and contact are returned', policy['configured'] and not policy['test_placeholder'] and policy['contact_email'] == 'operator@example.org' and policy['retention_summary'] == 'Isolated test policy' and policy['policy_url'] == 'https://operator.example.org/privacy')
        with patch.dict(os.environ, {'PRIVACY_CONTACT_EMAIL':'bad contact', 'PRIVACY_POLICY_URL':'javascript:alert(1)'}):
            policy = client.get('/api/privacy').json()
            check('unsafe contact and policy URL are not presented as usable', policy['contact_email'] is None and policy['policy_url'] is None and not policy['configured'])
        check('browser privacy page is public', client.get('/account/privacy.html').status_code == 200)
        check('discovery remains authenticated', client.get('/api/services').status_code == 401)
        bad = client.post('/api/auth/register', json={'display_name':'A', 'email':'person@example.org', 'password':'memory-only-valid-password', 'confirmation':'different-password'})
        check('password mismatch identifies confirmation without secret echo', bad.status_code == 422 and bad.json()['detail'][0]['loc'] == ['body','confirmation'] and 'memory-only' not in bad.text)
        bad = client.post('/api/auth/register', json={'display_name':' ', 'email':'bad email', 'password':'tinyZ9!', 'confirmation':'tinyZ9!'})
        check('validation returns safe field locations', bad.status_code == 422 and {item['loc'][-1] for item in bad.json()['detail']} >= {'display_name','email','password'} and 'tinyZ9!' not in bad.text)
        with Session.begin() as db:
            for account_id in ['retry-user-a','retry-user-b']:
                db.add(Account(id=account_id, email=account_id+'@example.org', display_name='Retry test', password_hash=auth.hasher.hash('isolated-retry-password'), state='ACTIVE', role='REGISTERED_USER'))
        def credentials(email):
            login = client.post('/api/auth/login', json={'email':email, 'password':'isolated-retry-password'}).json()
            return {'Authorization':'Bearer '+login['token']}
        first_headers = credentials('retry-user-a@example.org')
        second_headers = credentials('retry-user-b@example.org')
        future = (datetime.now(ZoneInfo('Asia/Singapore')) + timedelta(days=1)).replace(hour=12, minute=30, second=0, microsecond=0)
        with Session.begin() as db:
            service = db.get(Record, 'harmony')
            service.data = {**service.data, 'schedules':[{'days':list(range(7)), 'start':'12:00', 'end':'14:00'}],
                            'observations':[{'status':'AVAILABLE','observed_at':(datetime.now(timezone.utc)-timedelta(hours=1)).isoformat(),
                                             'valid_until':(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat(), 'source':'Isolated test'}]}
        projected = client.get('/api/services/harmony', headers=first_headers, params={'preferred_time':future.isoformat()})
        check('detail evaluates operating hours and availability at selected future time', projected.status_code == 200 and projected.json()['operating_status'] == 'OPEN' and projected.json()['availability'] == 'UNKNOWN' and projected.json()['status_at'] == future.isoformat())
        closed = client.get('/api/services/harmony', headers=first_headers, params={'preferred_time':future.replace(hour=18).isoformat()})
        check('future detail does not fall back to current opening status', closed.status_code == 200 and closed.json()['operating_status'] == 'CLOSED')
        for value in [future.replace(tzinfo=None).isoformat(), (future-timedelta(days=2)).isoformat(), 'not-a-date']:
            check('detail rejects invalid, past or timezone-free preferred time '+value, client.get('/api/services/harmony', headers=first_headers, params={'preferred_time':value}).status_code == 422)
        payload = {'submission_id':str(uuid4()),'service_id':'harmony','category':'Hours','description':'Please check these hours.','evidence':'https://example.org/programme'}
        with ThreadPoolExecutor(max_workers=4) as pool:
            responses = list(pool.map(lambda _:client.post('/api/reports', headers=first_headers,json=payload),range(4)))
        reference = responses[0].json()['reference']
        check('concurrent identical sends all recover one receipt', all(response.status_code == 201 and response.json() == responses[0].json() for response in responses))
        check('replay is indicated without inventing status', sum(response.headers.get('Idempotency-Replayed') == 'true' for response in responses) == 3)
        with Session() as db:
            reports = list(db.scalars(select(Record).where(Record.kind == 'report')))
            check('only one report persisted and reporter is server derived', len(reports) == 1 and reports[0].data['reporter_account_id'] == 'retry-user-a')
        altered = client.post('/api/reports',headers=first_headers,json={**payload,'description':'Changed text'})
        check('reusing key with edited payload is rejected', altered.status_code == 409)
        other = client.post('/api/reports',headers=second_headers,json=payload)
        check('same client key is isolated by account', other.status_code == 201 and other.json()['reference'] != reference)
        new = client.post('/api/reports',headers=first_headers,json={**payload,'submission_id':str(uuid4())})
        check('new submission key creates a new report', new.status_code == 201 and new.json()['reference'] != reference)
        invalid = client.post('/api/reports',headers=first_headers,json={**payload,'submission_id':'invalid-key'})
        check('submission key must be a UUID', invalid.status_code == 422)
        invalid = client.post('/api/reports',headers=first_headers,json={**payload,'evidence':'https://'})
        check('evidence error identifies malformed website field', invalid.status_code == 422 and invalid.json()['detail'][0]['loc'][-1] == 'evidence')
        with Session.begin() as db:
            service = db.get(Record,'harmony'); service.data = {**service.data, 'active':False}
            db.get(Record,reference).state = 'APPROVED'
        retry = client.post('/api/reports',headers=first_headers,json=payload)
        check('uncertain send is recoverable after target retirement and assessment', retry.status_code == 201 and retry.json()['reference'] == reference and retry.json()['state'] == 'APPROVED' and retry.json()['submitted_at'] == responses[0].json()['submitted_at'])
finally:
    engine.dispose()
    temporary.cleanup()
print(f'{len(passed)} flow recovery acceptance checks passed.')
