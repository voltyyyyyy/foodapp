"""Provision an administrator using a user ID and a password from stdin or a prompt."""
import argparse
import getpass
import sys
from uuid import uuid4

from argon2 import PasswordHasher
from sqlalchemy import delete

from .db import Admin, Audit, Base, Session, Token, engine


def main():
    parser = argparse.ArgumentParser(description='Create or reset a FoodBridge administrator')
    parser.add_argument('--user-id', required=True)
    parser.add_argument('--password-stdin', action='store_true', help='Read one password line without echoing it')
    args = parser.parse_args()
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
