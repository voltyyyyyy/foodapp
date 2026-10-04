"""Withdraw fictional fixtures while retaining their history and an audit trail."""
from uuid import uuid4
from sqlalchemy import select
from .db import Audit, Record, Session, now

def retire_demo():
    count = 0
    with Session.begin() as db:
        for record in db.scalars(select(Record)):
            if not record.data.get('demo') and record.id != 'demo-source':
                continue
            if record.state == 'WITHDRAWN':
                continue
            before = {'state': record.state, 'data': dict(record.data)}
            record.state = 'WITHDRAWN'
            record.data = {**record.data, 'active': False}
            record.version += 1
            record.updated_at = now()
            db.add(Audit(id=str(uuid4()), actor='local-maintenance', action='WITHDRAW_DEMO',
                target=record.id, detail={'before': before, 'reason': 'User requested real listings only'}))
            count += 1
    print(f'Withdrew {count} fictional records; actual listings preserved.')

if __name__ == '__main__':
    retire_demo()
