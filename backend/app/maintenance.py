"""Run hourly for expiry and daily for off-host backup integration."""
import argparse
import json
import os
import subprocess
from datetime import datetime, timedelta
from pathlib import Path

from sqlalchemy import select

from .db import Base, Record, Session, engine, iso, now

def expire():
    from sqlalchemy import delete, or_
    from .db import AccountSession, AccountLink, GoogleChallenge
    changed = 0
    with Session.begin() as db:
        db.execute(delete(AccountSession).where(or_(AccountSession.expires_at <= now(), AccountSession.last_activity <= now() - timedelta(minutes=30))))
        db.execute(delete(AccountLink).where(AccountLink.expires_at <= now()))
        db.execute(delete(GoogleChallenge).where(GoogleChallenge.expires_at <= now()))
        for record in db.scalars(select(Record).where(Record.kind == 'service')):
            data = dict(record.data)
            observations = [dict(o) for o in data.get('observations', [])]
            dirty = False
            for observation in observations:
                if observation['status'] != 'EXPIRED' and datetime.fromisoformat(observation['valid_until']) <= now():
                    observation['original_status'] = observation['status']
                    observation['status'] = 'EXPIRED'
                    dirty = True
                    changed += 1
            if dirty:
                data['observations'] = observations
                record.data = data
        for session in db.query(__import__('app.db', fromlist=['Token']).Token).all():
            if datetime.fromisoformat(iso(session.expires_at)) <= now():
                db.delete(session)
    return changed

def backup(directory):
    path = Path(directory)
    path.mkdir(parents=True, exist_ok=True)
    filename = path / ('foodbridge-' + now().strftime('%Y%m%dT%H%M%SZ'))
    if engine.dialect.name == 'sqlite':
        import sqlite3
        filename = filename.with_suffix('.sqlite')
        source = sqlite3.connect(engine.url.database)
        destination = sqlite3.connect(filename)
        with destination:
            source.backup(destination)
        source.close()
        destination.close()
    else:
        filename = filename.with_suffix('.dump')
        env = {**os.environ, 'PGPASSWORD': engine.url.password or ''}
        subprocess.run(['pg_dump', '--host', engine.url.host, '--port', str(engine.url.port or 5432),
            '--username', engine.url.username, '--dbname', engine.url.database, '--format=custom', '--file', str(filename)], check=True, env=env)
    (path / 'last-success.json').write_text(json.dumps({'completed_at': iso(now()), 'file': str(filename)}))
    return filename

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--backup-dir')
    args = parser.parse_args()
    print(f'Expired {expire()} observations')
    if args.backup_dir:
        print(f'Backup complete: {backup(args.backup_dir)}')
