"""Registered accounts, bounded sessions, email challenges and Google token verification."""
import hashlib
import os
import secrets
import smtplib
import ssl
from datetime import datetime, timedelta
from contextlib import contextmanager
from email.message import EmailMessage
from uuid import uuid4

from argon2 import PasswordHasher
from argon2.exceptions import VerificationError
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError

from .db import Account, AccountLink, AccountSession, GoogleChallenge, Session, iso, now

router = APIRouter(prefix='/api/auth', tags=['Accounts'])
security = HTTPBearer(auto_error=False)
hasher = PasswordHasher()
dummy_hash = hasher.hash(secrets.token_urlsafe(32))
INVALID = 'Email or password is incorrect, or the account is not active.'
ACK = 'If an eligible account exists, an email will arrive with the next steps.'

@contextmanager
def transaction(serialize=True):
    with Session() as db:
        try:
            # SQLite has no row locks. Serialize account writes before reading counters or
            # one-use links; PostgreSQL uses the explicit FOR UPDATE account locks below.
            if serialize and db.bind.dialect.name == 'sqlite':
                db.connection().exec_driver_sql('BEGIN IMMEDIATE')
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

def timestamp(value):
    return datetime.fromisoformat(iso(value))

def public_account(account):
    return {'id': account.id, 'email': account.email, 'display_name': account.display_name,
            'role': account.role, 'has_password': account.password_hash is not None}

def locked(db, account_id):
    return db.scalar(select(Account).where(Account.id == account_id).with_for_update())

def session_account(credentials=Depends(security)):
    if not credentials:
        raise HTTPException(401, 'Sign in to continue.')
    with transaction(serialize=False) as db:
        # Serialize session use with password/reset/revocation on the account row.
        session = db.get(AccountSession, digest(credentials.credentials))
        account = locked(db, session.account_id) if session else None
        if session:
            session = db.scalar(select(AccountSession).where(AccountSession.digest == session.digest).execution_options(populate_existing=True))
        if (not session or not account or account.state != 'ACTIVE'
                or timestamp(session.expires_at) <= now()
                or timestamp(session.last_activity) + timedelta(minutes=30) <= now()):
            raise HTTPException(401, 'Your session has expired. Sign in again.')
        session.last_activity = now()
        return public_account(account)

def administrator(account=Depends(session_account)):
    if account['role'] != 'ADMINISTRATOR':
        raise HTTPException(403, 'Administrator access required.')
    return account['email']

def new_session(db, account):
    token = secrets.token_urlsafe(48)
    issued = now()
    db.add(AccountSession(digest=digest(token), account_id=account.id, issued_at=issued,
                          last_activity=issued, expires_at=issued + timedelta(hours=24)))
    return {'token': token, 'account': public_account(account), 'expires_at': iso(issued + timedelta(hours=24))}

def revoke(db, account_id):
    db.execute(delete(AccountSession).where(AccountSession.account_id == account_id))
    db.execute(delete(AccountLink).where(AccountLink.account_id == account_id, AccountLink.purpose == 'reset'))

class Input(BaseModel):
    model_config = ConfigDict(extra='forbid', hide_input_in_errors=True)

class EmailInput(Input):
    email: EmailStr = Field(max_length=254)

    @field_validator('email', mode='before')
    @classmethod
    def normalize(cls, value):
        return value.strip().lower() if isinstance(value, str) else value

class NameInput(Input):
    display_name: str = Field(min_length=1, max_length=100)

    @field_validator('display_name', mode='before')
    @classmethod
    def trim_name(cls, value):
        return value.strip() if isinstance(value, str) else value

class NewPassword(Input):
    password: str = Field(min_length=12, max_length=128)
    confirmation: str = Field(max_length=128)

    @model_validator(mode='after')
    def confirmation_matches(self):
        if self.password != self.confirmation:
            raise ValueError('Password confirmation must match.')
        return self

class Registration(EmailInput, NameInput, NewPassword):
    pass

class Login(EmailInput):
    password: str = Field(max_length=128)

class LinkInput(Input):
    token: str = Field(min_length=20, max_length=200)

class Reset(LinkInput, NewPassword):
    pass

class PasswordChange(NewPassword):
    current_password: str = Field(max_length=128)

def throttle(request, action, limit=20):
    # Share the bounded, salted request limiter with other endpoints.
    from .main import throttle as limit_requests
    limit_requests(request, action, limit)

def mail_configured():
    return bool(os.getenv('SMTP_HOST') and os.getenv('SMTP_FROM') and os.getenv('AUTH_PUBLIC_URL'))

def require_mail():
    if not mail_configured():
        raise HTTPException(503, 'Account email is unavailable. Please try again later.')
    if os.getenv('ENVIRONMENT') == 'production' and not os.environ['AUTH_PUBLIC_URL'].startswith('https://'):
        raise HTTPException(503, 'Account email is unavailable. Please try again later.')

def issue_link(db, account, purpose):
    db.execute(delete(AccountLink).where(AccountLink.account_id == account.id, AccountLink.purpose == purpose))
    token = secrets.token_urlsafe(48)
    db.add(AccountLink(digest=digest(token), account_id=account.id, purpose=purpose,
                      expires_at=now() + (timedelta(hours=24) if purpose == 'verify' else timedelta(minutes=30))))
    return token

def send_link(email, purpose, token):
    # Fragments are not sent in HTTP requests or access logs. The page posts the token in a body.
    link = os.environ['AUTH_PUBLIC_URL'].rstrip('/') + '/account/#' + purpose + '=' + token
    message = EmailMessage()
    message['From'] = os.environ['SMTP_FROM']
    message['To'] = email
    message['Subject'] = 'Verify your FoodBridge email' if purpose == 'verify' else 'Reset your FoodBridge password'
    lifetime = '24 hours' if purpose == 'verify' else '30 minutes'
    message.set_content(f'Open this link to {"verify your email" if purpose == "verify" else "reset your password"}:\n\n{link}\n\n'
                        f'This link can be used once and expires in {lifetime}. If you did not request it, ignore this email.')
    try:
        use_ssl = os.getenv('SMTP_SSL', 'false').lower() == 'true'
        mailer = smtplib.SMTP_SSL if use_ssl else smtplib.SMTP
        with mailer(os.environ['SMTP_HOST'], int(os.getenv('SMTP_PORT', '465' if use_ssl else '587')), timeout=10) as smtp:
            if not use_ssl:
                smtp.starttls(context=ssl.create_default_context())
            if os.getenv('SMTP_USERNAME'):
                smtp.login(os.environ['SMTP_USERNAME'], os.environ.get('SMTP_PASSWORD', ''))
            smtp.send_message(message)
    except (OSError, smtplib.SMTPException):
        raise HTTPException(503, 'Account email could not be delivered. Please request a new link later.') from None

@router.post('/register', status_code=201)
def register(body: Registration, request: Request):
    throttle(request, 'register', 10)
    require_mail()
    try:
        with transaction() as db:
            if db.scalar(select(Account).where(Account.email == str(body.email))):
                raise HTTPException(409, 'An account already uses this email. Sign in or reset your password.')
            account = Account(id=str(uuid4()), email=str(body.email), display_name=body.display_name,
                              password_hash=hasher.hash(body.password), role='REGISTERED_USER', state='PENDING_VERIFICATION')
            db.add(account)
            db.flush()
            token = issue_link(db, account, 'verify')
    except IntegrityError:
        raise HTTPException(409, 'An account already uses this email. Sign in or reset your password.') from None
    send_link(str(body.email), 'verify', token)
    return {'message': 'Check your email to activate your account. You can then sign in.'}

@router.post('/login')
def login(body: Login, request: Request):
    throttle(request, 'account-login', 40)
    result = None
    with transaction() as db:
        account = db.scalar(select(Account).where(Account.email == str(body.email)).with_for_update())
        blocked = account and account.blocked_until and timestamp(account.blocked_until) > now()
        if blocked:
            # Perform equivalent password work without allowing a blocked account to authenticate.
            encoded = dummy_hash
        else:
            encoded = account.password_hash if account and account.password_hash else dummy_hash
        try:
            valid = hasher.verify(encoded, body.password)
        except VerificationError:
            valid = False
        if account and not blocked:
            if account.blocked_until and timestamp(account.blocked_until) <= now():
                account.failed_attempts = 0
                account.blocked_until = None
            if not valid:
                account.failed_attempts += 1
                if account.failed_attempts >= 5:
                    account.blocked_until = now() + timedelta(minutes=15)
            elif account.state == 'ACTIVE':
                account.failed_attempts = 0
                account.blocked_until = None
                if hasher.check_needs_rehash(account.password_hash):
                    account.password_hash = hasher.hash(body.password)
                result = new_session(db, account)
    # Raise after committing failed-attempt counters.
    if not result:
        raise HTTPException(401, INVALID)
    return result

@router.post('/verify')
def verify(body: LinkInput, request: Request):
    throttle(request, 'verify')
    consume_link(body.token, 'verify')
    return {'message': 'Email verified. You can now sign in.'}

def consume_link(token, purpose, password=None):
    with transaction() as db:
        link = db.get(AccountLink, digest(token))
        account = locked(db, link.account_id) if link else None
        if link:
            link = db.scalar(select(AccountLink).where(AccountLink.digest == link.digest).execution_options(populate_existing=True))
        if (not link or not account or link.purpose != purpose or timestamp(link.expires_at) <= now()
                or account.state != ('PENDING_VERIFICATION' if purpose == 'verify' else 'ACTIVE')):
            raise HTTPException(400, 'This link is invalid, expired, or already used. Request a new link.')
        if purpose == 'verify':
            account.state = 'ACTIVE'
        else:
            account.password_hash = hasher.hash(password)
            account.failed_attempts = 0
            account.blocked_until = None
            revoke(db, account.id)
        db.execute(delete(AccountLink).where(AccountLink.account_id == account.id, AccountLink.purpose == purpose))

def request_link(body, request, purpose):
    throttle(request, 'request-' + purpose, 10)
    require_mail()
    token = None
    with transaction() as db:
        account = db.scalar(select(Account).where(Account.email == str(body.email)).with_for_update())
        if account and account.state == ('PENDING_VERIFICATION' if purpose == 'verify' else 'ACTIVE'):
            token = issue_link(db, account, purpose)
    if token:
        try:
            send_link(str(body.email), purpose, token)
        except HTTPException:
            # Keep the same acknowledgement for eligible and unknown addresses.
            # The user can retry; no tokens or addresses are written to application logs.
            pass
    return {'message': ACK}

@router.post('/resend-verification')
def resend(body: EmailInput, request: Request):
    return request_link(body, request, 'verify')

@router.post('/forgot-password')
def forgot(body: EmailInput, request: Request):
    return request_link(body, request, 'reset')

@router.post('/reset-password')
def reset(body: Reset, request: Request):
    throttle(request, 'reset')
    consume_link(body.token, 'reset', body.password)
    return {'message': 'Password reset. Sign in with your new password.'}

@router.get('/me')
def me(account=Depends(session_account)):
    return account

@router.patch('/me')
def edit_profile(body: NameInput, account=Depends(session_account)):
    with transaction() as db:
        stored = locked(db, account['id'])
        stored.display_name = body.display_name
        return public_account(stored)

@router.post('/change-password')
def change_password(body: PasswordChange, request: Request, account=Depends(session_account)):
    throttle(request, 'change-password', 10)
    with transaction() as db:
        stored = locked(db, account['id'])
        try:
            hasher.verify(stored.password_hash or dummy_hash, body.current_password)
        except VerificationError:
            raise HTTPException(400, 'Current password is incorrect.') from None
        if not stored.password_hash:
            raise HTTPException(400, 'Use password reset to set a password for this Google account.')
        stored.password_hash = hasher.hash(body.password)
        revoke(db, stored.id)
    return {'message': 'Password changed. Sign in again.'}

@router.post('/logout')
def logout(credentials: HTTPAuthorizationCredentials | None = Depends(security)):
    # Idempotent, including revoked/expired tokens.
    if credentials:
        with transaction() as db:
            db.execute(delete(AccountSession).where(AccountSession.digest == digest(credentials.credentials)))
    return {'status': 'signed_out'}

@router.post('/google/challenge')
def google_challenge(request: Request):
    throttle(request, 'google-challenge')
    if not os.getenv('GOOGLE_WEB_CLIENT_ID'):
        raise HTTPException(503, 'Google sign-in is not configured. Use email and password.')
    nonce = secrets.token_urlsafe(32)
    with transaction() as db:
        db.execute(delete(GoogleChallenge).where(GoogleChallenge.expires_at <= now()))
        db.add(GoogleChallenge(digest=digest(nonce), expires_at=now() + timedelta(minutes=5)))
    return {'nonce': nonce}

class GoogleInput(Input):
    id_token: str = Field(min_length=20, max_length=10000)
    nonce: str = Field(min_length=20, max_length=200)

def verify_google_token(token, client_id):
    from google.auth.transport.requests import Request as GoogleRequest
    from google.oauth2 import id_token
    # google-auth checks Google's signature, issuer, audience and expiry.
    class BoundedRequest(GoogleRequest):
        def __call__(self, *args, **kwargs):
            kwargs['timeout'] = 5
            return super().__call__(*args, **kwargs)
    return id_token.verify_oauth2_token(token, BoundedRequest(), client_id)

@router.post('/google')
def google_login(body: GoogleInput, request: Request):
    from google.auth.exceptions import GoogleAuthError, TransportError
    throttle(request, 'google-login')
    client_id = os.getenv('GOOGLE_WEB_CLIENT_ID')
    if not client_id:
        raise HTTPException(503, 'Google sign-in is not configured. Use email and password.')
    try:
        claims = verify_google_token(body.id_token, client_id)
        if (claims.get('nonce') != body.nonce or claims.get('email_verified') is not True
                or not claims.get('sub') or not claims.get('email')):
            raise ValueError('Invalid identity')
        email = str(EmailInput(email=claims['email']).email)
    except TransportError:
        raise HTTPException(503, 'Google sign-in is unavailable. Please try again later.') from None
    except (ValueError, TypeError, GoogleAuthError):
        raise HTTPException(401, 'Google sign-in could not be verified. Please try again.') from None
    except Exception:
        raise HTTPException(503, 'Google sign-in is unavailable. Please try again later.') from None
    try:
        with transaction() as db:
            challenge = db.execute(delete(GoogleChallenge).where(GoogleChallenge.digest == digest(body.nonce),
                                   GoogleChallenge.expires_at > now()))
            if challenge.rowcount != 1:
                raise HTTPException(401, 'Google sign-in has expired. Please try again.')
            account = db.scalar(select(Account).where(Account.google_subject == claims['sub']).with_for_update())
            if not account:
                existing = db.scalar(select(Account).where(Account.email == email).with_for_update())
                if existing:
                    # Never link by email alone. Existing accounts keep their existing credentials.
                    raise HTTPException(409, 'This email already has an account. Sign in with your password or reset it.')
                account = Account(id=str(uuid4()), email=email, display_name=(claims.get('name') or email.split('@')[0]).strip()[:100] or 'FoodBridge user',
                                  google_subject=claims['sub'], role='REGISTERED_USER', state='ACTIVE')
                db.add(account)
                db.flush()
            if account.state != 'ACTIVE':
                raise HTTPException(401, INVALID)
            return new_session(db, account)
    except IntegrityError:
        raise HTTPException(409, 'An account already uses this email. Sign in using your existing account.') from None
