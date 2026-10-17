"""Account acceptance checks. Uses only a fresh temporary database and in-memory email capture."""
import os
import sys
import tempfile
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch
from concurrent.futures import ThreadPoolExecutor

temporary = tempfile.TemporaryDirectory(prefix='foodbridge-accounts-')
os.environ.update(DATABASE_URL='sqlite:///' + (Path(temporary.name) / 'accounts.db').as_posix(),
                  ENVIRONMENT='development', DEMO_DATA='true', SMTP_HOST='mail.example.com',
                  SMTP_FROM='foodbridge@example.com', AUTH_PUBLIC_URL='https://foodbridge.example.com',
                  GOOGLE_WEB_CLIENT_ID='acceptance.apps.googleusercontent.com',
                  ADMIN_EMAIL='operator@example.com', ADMIN_PASSWORD='isolated-operator-password')
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient
from sqlalchemy import select
from app.main import app
from app import auth
from app.db import Account, AccountLink, AccountSession, Record, Session, engine, now

passed = []
sent = []
def check(name, condition):
    assert condition, name
    passed.append(name)
    print('PASS:', name)

def send(email, purpose, token):
    sent.append((email, purpose, token))

def body(email='seeker@example.com'):
    return {'display_name': '  Food seeker  ', 'email': email, 'password': 'correct-long-password', 'confirmation': 'correct-long-password'}

def login(client, email='seeker@example.com', password='correct-long-password'):
    return client.post('/api/auth/login', json={'email': email, 'password': password})

def last_token(purpose):
    return next(token for _, kind, token in reversed(sent) if kind == purpose)

with patch.object(auth, 'send_link', side_effect=send), TestClient(app) as client:
    for path in ['/api/services', '/api/services/harmony', '/api/providers', '/api/auth/me', '/api/admin/reports']:
        check('anonymous denied ' + path, client.get(path).status_code == 401)
    for path in ['/api/search', '/api/geocode', '/api/routes', '/api/reports']:
        check('anonymous denied ' + path, client.post(path, json={}).status_code == 401)
    invalid = client.post('/api/auth/register', json={**body(), 'password': 'secret', 'confirmation': 'secret'})
    check('short password rejected without echoing secret', invalid.status_code == 422 and 'secret' not in invalid.text)
    for changes in [{'display_name': ' '}, {'display_name': 'x' * 101}, {'email': 'invalid'}, {'confirmation': 'different'}, {'role': 'ADMINISTRATOR'}, {'password': 'x' * 129}]:
        check('invalid registration rejected ' + next(iter(changes)), client.post('/api/auth/register', json={**body(), **changes}).status_code == 422)
    check('registration accepted', client.post('/api/auth/register', json=body(' SEEKER@EXAMPLE.COM ')).status_code == 201)
    token = last_token('verify')
    with Session() as db:
        account = db.scalar(select(Account).where(Account.email == 'seeker@example.com'))
        account_id = account.id
        link = db.get(AccountLink, auth.digest(token))
        check('new account pending with registered-user role', account.state == 'PENDING_VERIFICATION' and account.role == 'REGISTERED_USER')
        check('normalised email, trimmed name and adaptive hash', account.display_name == 'Food seeker' and account.password_hash.startswith('$argon2id$'))
        check('verification token is hashed and expires in 24h', link and 23.9 * 3600 < (auth.timestamp(link.expires_at) - now()).total_seconds() <= 24 * 3600)
    check('duplicate email rejected', client.post('/api/auth/register', json=body('Seeker@example.com')).status_code == 409)
    check('unverified account cannot sign in', login(client).status_code == 401)
    check('resend acknowledgement hides unknown address', client.post('/api/auth/resend-verification', json={'email': 'unknown@example.com'}).json() == client.post('/api/auth/resend-verification', json={'email': 'seeker@example.com'}).json())
    replacement = last_token('verify')
    check('replacement invalidates verification link', client.post('/api/auth/verify', json={'token': token}).status_code == 400)
    with Session.begin() as db:
        db.get(AccountLink, auth.digest(replacement)).expires_at = now() - timedelta(seconds=1)
    check('expired verification cannot activate account', client.post('/api/auth/verify', json={'token': replacement}).status_code == 400)
    client.post('/api/auth/resend-verification', json={'email': 'seeker@example.com'})
    replacement = last_token('verify')
    check('email verification activates account', client.post('/api/auth/verify', json={'token': replacement}).status_code == 200)
    check('verification is single use', client.post('/api/auth/verify', json={'token': replacement}).status_code == 400)
    result = login(client).json()
    credentials = {'Authorization': 'Bearer ' + result['token']}
    check('sign in returns account and bounded session', result['account']['id'] == account_id and 'expires_at' in result)
    with Session() as db:
        session = db.get(AccountSession, auth.digest(result['token']))
        check('server stores hashed session token', session and session.digest != result['token'])
    check('approved services available after sign in', client.get('/api/services', headers=credentials).status_code == 200)
    check('registered user cannot read administrator reports', client.get('/api/admin/reports', headers=credentials).status_code == 403)
    check('registered user cannot sign into staff portal', client.post('/api/admin/login', json={'email': 'seeker@example.com', 'password': 'correct-long-password'}).status_code == 403)
    check('own profile returned', client.get('/api/auth/me', headers=credentials).json()['id'] == account_id)
    for changes in [{'email': 'other@example.com'}, {'role': 'ADMINISTRATOR'}, {'display_name': ' '}]:
        check('protected profile fields rejected ' + next(iter(changes)), client.patch('/api/auth/me', headers=credentials, json=changes).status_code == 422)
    check('trimmed name update', client.patch('/api/auth/me', headers=credentials, json={'display_name': ' Updated name '}).json()['display_name'] == 'Updated name')
    report = client.post('/api/reports', headers=credentials, json={'service_id': 'harmony', 'category': 'Hours', 'description': 'Please check hours.'}).json()
    with Session() as db:
        check('correction records authenticated reporter', db.get(Record, report['reference']).data['reporter_account_id'] == account_id)
    check('invalid credentials use one generic error', login(client, password='incorrect').json() == login(client, email='missing@example.com', password='incorrect').json())
    for _ in range(4):
        login(client, password='incorrect')
    check('fifth failure blocks correct password', login(client).status_code == 401)
    with Session.begin() as db:
        account = db.get(Account, account_id)
        check('15 minute lockout persisted', account.failed_attempts == 5 and auth.timestamp(account.blocked_until) > now() + timedelta(minutes=14))
        account.blocked_until = now() - timedelta(seconds=1)
    check('password sign in allowed after lockout', login(client).status_code == 200)
    with Session() as db:
        check('successful sign in resets failure count', db.get(Account, account_id).failed_attempts == 0)
    check('password reset acknowledgement hides unknown address', client.post('/api/auth/forgot-password', json={'email': 'missing@example.com'}).json() == client.post('/api/auth/forgot-password', json={'email': 'seeker@example.com'}).json())
    reset_token = last_token('reset')
    with Session() as db:
        check('reset expires in 30 minutes', 29 * 60 < (auth.timestamp(db.get(AccountLink, auth.digest(reset_token)).expires_at) - now()).total_seconds() <= 30 * 60)
    client.post('/api/auth/forgot-password', json={'email': 'seeker@example.com'})
    new_reset = last_token('reset')
    reset_body = {'token': new_reset, 'password': 'replacement-password', 'confirmation': 'replacement-password'}
    check('replacement invalidates earlier reset link', client.post('/api/auth/reset-password', json={**reset_body, 'token': reset_token}).status_code == 400)
    check('reset confirmation validated', client.post('/api/auth/reset-password', json={**reset_body, 'confirmation': 'wrong'}).status_code == 422)
    check('password reset accepted', client.post('/api/auth/reset-password', json=reset_body).status_code == 200)
    check('reset revokes all old sessions', client.get('/api/services', headers=credentials).status_code == 401)
    check('reset link cannot be reused', client.post('/api/auth/reset-password', json=reset_body).status_code == 400)
    check('old password rejected after reset', login(client).status_code == 401)
    result = login(client, password='replacement-password').json()
    credentials = {'Authorization': 'Bearer ' + result['token']}
    change = {'current_password': 'incorrect', 'password': 'newly-changed-password', 'confirmation': 'newly-changed-password'}
    check('wrong current password rejected', client.post('/api/auth/change-password', headers=credentials, json=change).status_code == 400)
    check('password unchanged after failed change', login(client, password='replacement-password').status_code == 200)
    check('password change succeeds', client.post('/api/auth/change-password', headers=credentials, json={**change, 'current_password': 'replacement-password'}).status_code == 200)
    check('password change revokes old sessions', client.get('/api/auth/me', headers=credentials).status_code == 401)
    result = login(client, password='newly-changed-password').json()
    credentials = {'Authorization': 'Bearer ' + result['token']}
    with Session.begin() as db:
        db.get(AccountSession, auth.digest(result['token'])).last_activity = now() - timedelta(minutes=31)
    check('30 minute inactivity enforced', client.get('/api/services', headers=credentials).status_code == 401)
    result = login(client, password='newly-changed-password').json()
    credentials = {'Authorization': 'Bearer ' + result['token']}
    with Session.begin() as db:
        db.get(AccountSession, auth.digest(result['token'])).expires_at = now() - timedelta(seconds=1)
    check('24 hour absolute expiry enforced', client.get('/api/services', headers=credentials).status_code == 401)
    result = login(client, password='newly-changed-password').json()
    credentials = {'Authorization': 'Bearer ' + result['token']}
    check('sign out accepted', client.post('/api/auth/logout', headers=credentials).status_code == 200)
    check('signed out token denied', client.get('/api/services', headers=credentials).status_code == 401)
    check('sign out is idempotent', client.post('/api/auth/logout', headers=credentials).status_code == 200)

    # Real signed Google JWTs with an isolated certificate response: no Google account or network needed.
    import rsa
    from google.auth import crypt, jwt
    public, private = rsa.newkeys(1024)
    signer = crypt.RSASigner.from_string(private.save_pkcs1(), key_id='acceptance-key')
    claims = {'iss': 'https://accounts.google.com', 'aud': os.environ['GOOGLE_WEB_CLIENT_ID'],
              'sub': 'isolated-google-subject', 'email': 'google-user@gmail.com', 'email_verified': True,
              'name': 'Google user', 'iat': int(now().timestamp()), 'exp': int((now() + timedelta(hours=1)).timestamp())}
    with patch('google.oauth2.id_token._fetch_certs', return_value={'acceptance-key': public.save_pkcs1().decode()}):
        nonce = client.post('/api/auth/google/challenge').json()['nonce']
        def google_payload(**changes):
            return {'nonce': nonce, 'id_token': jwt.encode(signer, {**claims, 'nonce': nonce, **changes}).decode()}
        check('Google wrong audience rejected', client.post('/api/auth/google', json=google_payload(aud='other-client')).status_code == 401)
        altered = google_payload()
        segments = altered['id_token'].split('.')
        segments[2] = ('A' if segments[2][0] != 'A' else 'B') + segments[2][1:]
        check('Google altered signature rejected', client.post('/api/auth/google', json={**altered, 'id_token': '.'.join(segments)}).status_code == 401)
        check('Google wrong issuer rejected', client.post('/api/auth/google', json=google_payload(iss='https://attacker.example')).status_code == 401)
        check('Google expired ID token rejected', client.post('/api/auth/google', json=google_payload(exp=int((now() - timedelta(minutes=5)).timestamp()))).status_code == 401)
        check('Google unverified email rejected', client.post('/api/auth/google', json=google_payload(email_verified=False)).status_code == 401)
        check('Google nonce mismatch rejected', client.post('/api/auth/google', json=google_payload(nonce='mismatched-nonce')).status_code == 401)
        google = client.post('/api/auth/google', json=google_payload())
        check('verified Google account signs in', google.status_code == 200 and google.json()['account']['role'] == 'REGISTERED_USER')
        check('Google challenge cannot be reused', client.post('/api/auth/google', json=google_payload()).status_code == 401)
        nonce = client.post('/api/auth/google/challenge').json()['nonce']
        repeated = client.post('/api/auth/google', json=google_payload())
        check('Google subject maps to stable account', repeated.json()['account']['id'] == google.json()['account']['id'])
        nonce = client.post('/api/auth/google/challenge').json()['nonce']
        check('email collision never links an account silently', client.post('/api/auth/google', json=google_payload(sub='different-subject', email='seeker@example.com')).status_code == 409)
    with Session.begin() as db:
        db.add(Account(id='concurrent-account', email='concurrent@example.com', display_name='Concurrent fixture',
                       password_hash=auth.hasher.hash('concurrent-account-password'), role='REGISTERED_USER', state='ACTIVE'))
    with ThreadPoolExecutor(max_workers=6) as pool:
        failures = list(pool.map(lambda _: login(client, 'concurrent@example.com', 'wrong-password').status_code, range(6)))
    with Session() as db:
        stored = db.get(Account, 'concurrent-account')
        check('concurrent failed passwords cannot lose lockout counts', failures == [401] * 6 and stored.failed_attempts == 5 and auth.timestamp(stored.blocked_until) > now())
    client.post('/api/auth/forgot-password', json={'email': 'seeker@example.com'})
    concurrent_reset = {'token': last_token('reset'), 'password': 'concurrent-reset-password', 'confirmation': 'concurrent-reset-password'}
    with ThreadPoolExecutor(max_workers=2) as pool:
        resets = list(pool.map(lambda _: client.post('/api/auth/reset-password', json=concurrent_reset).status_code, range(2)))
    check('concurrent reset consumes a link exactly once', sorted(resets) == [200, 400])
    with patch.dict(os.environ, {'ENVIRONMENT': 'production'}):
        check('production rejects HTTP credentials', login(client).status_code == 426)
    with patch.dict(os.environ, {'SMTP_HOST': ''}):
        check('missing email service fails honestly', client.post('/api/auth/register', json=body('another@example.com')).status_code == 503)

engine.dispose()
temporary.cleanup()
print(f'{len(passed)} account acceptance checks passed.')
