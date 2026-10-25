"""Google account reuse checks against a disposable database and signed fixture tokens."""
import os
import sys
import tempfile
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch

temporary = tempfile.TemporaryDirectory(prefix='foodbridge-google-linking-')
os.environ.update(DATABASE_URL='sqlite:///' + (Path(temporary.name) / 'accounts.db').as_posix(),
                  ENVIRONMENT='development', DEMO_DATA='false',
                  GOOGLE_WEB_CLIENT_ID='acceptance.apps.googleusercontent.com')
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import rsa
from fastapi.testclient import TestClient
from google.auth import crypt, jwt
from sqlalchemy import select
from app import auth
from app.main import app
from app.db import Account, AccountLink, AccountSession, Session, engine, now

passed = []


def check(name, condition):
    assert condition, name
    passed.append(name)
    print('PASS:', name)


def fixture(db, identity, email, **changes):
    account = Account(id=identity, email=email, display_name='Existing name',
                      password_hash=auth.hasher.hash('existing-account-password'),
                      role='REGISTERED_USER', state='ACTIVE')
    for key, value in changes.items():
        setattr(account, key, value)
    db.add(account)
    return account


public, private = rsa.newkeys(1024)
signer = crypt.RSASigner.from_string(private.save_pkcs1(), key_id='linking-key')
with TestClient(app) as client, patch('google.oauth2.id_token._fetch_certs',
                                    return_value={'linking-key': public.save_pkcs1().decode()}):
    with Session.begin() as db:
        gmail = fixture(db, 'gmail', 'existing@gmail.com', failed_attempts=5,
                        blocked_until=now() + timedelta(minutes=10))
        original_hash = gmail.password_hash
        fixture(db, 'workspace', 'member@workspace.example.com')
        fixture(db, 'third-party', 'member@example.com')
        fixture(db, 'pending', 'pending@gmail.com', state='PENDING_VERIFICATION')
        fixture(db, 'other-subject', 'occupied@gmail.com', google_subject='original-subject')
        fixture(db, 'suspended', 'suspended@gmail.com', state='SUSPENDED')
        fixture(db, 'administrator', 'operator@gmail.com', role='ADMINISTRATOR')
        db.flush()
        db.add(AccountSession(digest=auth.digest('unverified-old-session'), account_id='pending',
                              issued_at=now(), last_activity=now(), expires_at=now() + timedelta(hours=1)))
        for purpose in ('verify', 'reset'):
            db.add(AccountLink(digest=auth.digest('pending-' + purpose), account_id='pending',
                               purpose=purpose, expires_at=now() + timedelta(hours=1)))

    def google(email, subject, **changes):
        nonce = client.post('/api/auth/google/challenge').json()['nonce']
        claims = dict(iss='https://accounts.google.com', aud=os.environ['GOOGLE_WEB_CLIENT_ID'],
                      email=email, email_verified=True, sub=subject, name='Verified Google name',
                      nonce=nonce, iat=int(now().timestamp()), exp=int((now() + timedelta(hours=1)).timestamp()))
        claims.update(changes)
        payload = dict(nonce=nonce, id_token=jwt.encode(signer, claims).decode())
        return client.post('/api/auth/google', json=payload), payload

    linked, payload = google('EXISTING@gmail.com', 'gmail-subject')
    check('verified Gmail reuses the existing account', linked.status_code == 200 and linked.json()['account']['id'] == 'gmail')
    check('account name and password remain available', linked.json()['account']['display_name'] == 'Existing name' and linked.json()['account']['has_password'])
    with Session() as db:
        account = db.get(Account, 'gmail')
        check('Google subject saved without resetting password lockout', account.google_subject == 'gmail-subject' and account.password_hash == original_hash and account.failed_attempts == 5 and account.blocked_until is not None)
        check('Gmail linking does not create a duplicate account', len(list(db.scalars(select(Account).where(Account.email == 'existing@gmail.com')))) == 1)
    headers = {'Authorization': 'Bearer ' + linked.json()['token']}
    check('linked user can use discovery', client.get('/api/services', headers=headers).status_code == 200)
    check('linking does not grant staff access', client.get('/api/admin/reports', headers=headers).status_code == 403)
    check('successful linking consumes the nonce', client.post('/api/auth/google', json=payload).status_code == 401)
    again, _ = google('changed@gmail.com', 'gmail-subject')
    check('later sign-in uses stable Google subject', again.status_code == 200 and again.json()['account']['id'] == 'gmail')

    workspace, _ = google('member@workspace.example.com', 'workspace-subject', hd='workspace.example.com')
    check('verified matching Workspace domain can link', workspace.status_code == 200 and workspace.json()['account']['id'] == 'workspace')
    for description, changes in [('third-party email', {}), ('different hosted domain', {'hd': 'other.example.com'}), ('invalid hosted domain', {'hd': True})]:
        response, _ = google('member@example.com', 'third-party-subject', **changes)
        check(description + ' cannot link by email', response.status_code == 409)
    with Session() as db:
        check('rejected third-party linking leaves the account unchanged', db.get(Account, 'third-party').google_subject is None)

    pending, _ = google('pending@gmail.com', 'pending-subject')
    check('Google ownership activates an unfinished registration', pending.status_code == 200 and pending.json()['account']['id'] == 'pending' and not pending.json()['account']['has_password'])
    with Session() as db:
        account = db.get(Account, 'pending')
        check('unverified credentials and name are replaced', account.state == 'ACTIVE' and account.password_hash is None and account.display_name == 'Verified Google name')
        check('unverified sessions and email links are revoked', db.get(AccountSession, auth.digest('unverified-old-session')) is None and not list(db.scalars(select(AccountLink).where(AccountLink.account_id == 'pending'))))
    old_password = client.post('/api/auth/login', json={'email': 'pending@gmail.com', 'password': 'existing-account-password'})
    check('unverified password cannot access the claimed account', old_password.status_code == 401)

    for identity, email in [('other-subject', 'occupied@gmail.com'), ('suspended', 'suspended@gmail.com'), ('administrator', 'operator@gmail.com')]:
        response, _ = google(email, 'replacement-' + identity)
        check(identity + ' is not overwritten or enabled by email matching', response.status_code == 409)
    with Session() as db:
        check('existing Google subject is retained', db.get(Account, 'other-subject').google_subject == 'original-subject')
        check('suspended and staff accounts remain unchanged', db.get(Account, 'suspended').state == 'SUSPENDED' and db.get(Account, 'suspended').google_subject is None and db.get(Account, 'administrator').google_subject is None)

engine.dispose()
temporary.cleanup()
print(f'{len(passed)} Google account reuse checks passed.')
