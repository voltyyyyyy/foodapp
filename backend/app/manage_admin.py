"""Provision an administrator using a user ID and a password from stdin or a prompt."""
import argparse
import getpass
import sys
from uuid import uuid4

from argon2 import PasswordHasher
from sqlalchemy import delete

from .db import Account, Admin, Audit, Base, Session, Token, engine
from .auth import EmailInput, locked, revoke
from sqlalchemy import select


def main():
    parser = argparse.ArgumentParser(description='Create or reset a FoodBridge administrator')
    parser.add_argument('--user-id', help='Legacy User ID (retained for operator compatibility)')
    parser.add_argument('--email', help='Email address for an SRS administrator account')
    parser.add_argument('--display-name', default='FoodBridge administrator')
    parser.add_argument('--migrate-user-id', help='Copy an existing legacy administrator verifier to this email; no password reset')
    parser.add_argument('--password-stdin', action='store_true', help='Read one password line without echoing it')
    args = parser.parse_args()
    if args.email:
        email = str(EmailInput(email=args.email).email)
        name = args.display_name.strip()
        if not 1 <= len(name) <= 100:
            parser.error('Display name requires 1 to 100 characters')
        Base.metadata.create_all(engine)
        if args.migrate_user_id:
            with Session.begin() as db:
                legacy = db.get(Admin, args.migrate_user_id.strip().lower())
                if not legacy:
                    parser.error('Legacy administrator not found')
                if db.scalar(select(Account).where(Account.email == email)):
                    parser.error('Email already belongs to an account; migration will not overwrite it')
                db.add(Account(id=str(uuid4()), email=email, display_name=name, password_hash=legacy.password_hash,
                               role='ADMINISTRATOR', state='ACTIVE'))
                db.execute(delete(Token).where(Token.username == legacy.username))
                db.add(Audit(id=str(uuid4()), actor='operator', action='MIGRATE_ADMIN', target=email,
                             detail={'legacy_user_id': legacy.username}))
            print('Administrator migrated; existing password retained, legacy sessions revoked.')
            return
        password = sys.stdin.readline().rstrip('\r\n') if args.password_stdin else getpass.getpass('Password: ')
        if not 12 <= len(password) <= 128:
            parser.error('Password requires 12 to 128 characters')
        with Session.begin() as db:
            existing = db.scalar(select(Account).where(Account.email == email).with_for_update())
            if existing and existing.role != 'ADMINISTRATOR':
                parser.error('Email belongs to a registered user; this command does not promote accounts')
            action = 'RESET_ADMIN_PASSWORD' if existing else 'CREATE_ADMIN'
            if existing:
                existing.password_hash = PasswordHasher().hash(password)
                existing.failed_attempts = 0
                existing.blocked_until = None
                revoke(db, existing.id)
            else:
                db.add(Account(id=str(uuid4()), email=email, display_name=name, password_hash=PasswordHasher().hash(password),
                               role='ADMINISTRATOR', state='ACTIVE'))
            db.add(Audit(id=str(uuid4()), actor='operator', action=action, target=email, detail={'email': email}))
        print('Administrator provisioned.')
        return
    if not args.user_id or args.migrate_user_id:
        parser.error('Supply --email for a current administrator, or --user-id for legacy management')
    username = args.user_id.strip().lower()
    if not username or len(username) > 200 or any(c.isspace() for c in username):
        parser.error('User ID must contain 1 to 200 characters without spaces')
    password = sys.stdin.readline().rstrip('\r\n') if args.password_stdin else getpass.getpass('Password: ')
    if not password or len(password) > 200:
        parser.error('Password must contain 1 to 200 characters')
    Base.metadata.create_all(engine)
    with Session.begin() as db:
        existing = db.get(Admin, username)
        action = 'RESET_ADMIN_PASSWORD' if existing else 'CREATE_ADMIN'
        password_hash = PasswordHasher().hash(password)
        if existing:
            existing.password_hash = password_hash
            db.execute(delete(Token).where(Token.username == username))
        else:
            db.add(Admin(username=username, password_hash=password_hash))
        db.add(Audit(id=str(uuid4()), actor='operator', action=action, target=username,
                     detail={'user_id': username}))
    print(f'Administrator {username} is authorised.')


if __name__ == '__main__':
    main()
