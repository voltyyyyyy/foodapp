"""Single scheduled maintenance process; run alongside the API in production."""
import logging
import os
import time
from datetime import timedelta
from urllib.request import urlopen

from .db import Record, Session, iso, now
from .maintenance import backup, expire

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')

def record_status(status, completed=None, error=None):
    with Session.begin() as db:
        record = db.get(Record, 'daily-backup')
        data = {**(record.data if record else {}), 'name': 'Daily database backup', 'status': status,
            'last_attempt': iso(now())}
        if completed:
            data['last_success'] = iso(completed)
        if error:
            data['error'] = error
        if record:
            record.data = data
        else:
            db.add(Record(id='daily-backup', kind='operation', data=data))

def run():
    ready_url = os.getenv('API_READY_URL', 'http://api:8000/health/ready')
    while True:
        try:
            with urlopen(ready_url, timeout=5) as response:
                if response.status == 200:
                    break
        except Exception:
            pass
        time.sleep(10)
    last_expiry, last_backup = float('-inf'), float('-inf')
    while True:
        instant = time.monotonic()
        if instant - last_expiry >= 3600:
            try:
                logging.info('Expiry maintenance completed: %s observations', expire())
                last_expiry = instant
            except Exception:
                logging.exception('Expiry maintenance failed')
        # 20-hour cadence provides margin for the requirement of <=24h between backups.
        if instant - last_backup >= 20 * 3600:
            try:
                path = backup(os.getenv('BACKUP_DIR', '/backups'))
                record_status('HEALTHY', completed=now())
                last_backup = instant
                logging.info('Database backup completed: %s', path.name)
            except Exception as error:
                record_status('FAILED', error=type(error).__name__)
                logging.exception('Database backup failed; retrying within one minute')
        time.sleep(60)

if __name__ == '__main__':
    run()
