from datetime import timedelta

from sqlalchemy import select

from .db import Record, Session, iso, now

def seed():
    with Session.begin() as db:
        if db.scalar(select(Record).where(Record.kind == 'service')):
            return
        timestamp = now()
        fixtures = [
            ('harmony', 'Harmony Community Kitchen', 'Hot meals', '123 Ang Mo Kio Avenue 3', '560123', 1.3691, 103.8494, ['Vegetarian', 'Halal'], 'AVAILABLE', 'A warm meal, a welcoming space.', 'Walk-in. No referral required.', True, '12:00', '14:30'),
            ('northside', 'Northside Family Support', 'Food bank', '245 Ang Mo Kio Avenue 6', '560245', 1.3751, 103.8451, ['Halal'], 'LIMITED', 'Grocery essentials for your household.', 'Contact the team to confirm eligibility.', True, '09:00', '17:00'),
            ('pantry', 'Neighbourhood Community Pantry', 'Groceries', '411 Ang Mo Kio Avenue 10', '560411', 1.3628, 103.8552, [], 'UNKNOWN', 'Dry goods and everyday pantry staples.', 'Open to residents. Confirm collection requirements.', None, '10:00', '18:00'),
            ('garden', 'Garden Table Community Meals', 'Hot meals', '220 Bishan Street 23', '570220', 1.3578, 103.8485, ['Vegetarian', 'Vegan'], 'AVAILABLE', 'Plant-based lunches shared with care.', 'Anyone in need. Walk-in collection.', True, '11:30', '14:00'),
            ('east', 'Eastside Essentials', 'Groceries', '301 Serangoon Avenue 2', '550301', 1.3512, 103.8715, ['Halal'], 'UNAVAILABLE', 'A community-led essentials collection point.', 'Call ahead for collection arrangements.', False, '09:00', '16:00'),
        ]
        for id_, name, kind, address, postal, lat, lon, diets, status, description, eligibility, wheelchair, start, end in fixtures:
            org = f'org-{id_}'
            provenance = {'source': 'Fictional demonstration fixture', 'imported_at': iso(timestamp), 'verified_at': iso(timestamp), 'demo': True}
            db.add(Record(id=org, kind='organisation', data={'name': name, **provenance}))
            db.add(Record(id=id_, kind='service', data={
                'organisation_id': org, 'name': name, 'support_type': kind, 'address': address, 'postal': postal,
                'latitude': lat, 'longitude': lon, 'description': description, 'eligibility': eligibility,
                'diets': diets, 'wheelchair': wheelchair, 'phone': None, 'website': None,
                'schedules': [{'days': list(range(6)), 'start': start, 'end': end, 'label': 'Meal collection' if kind == 'Hot meals' else 'Collection'}],
                'exceptions': {}, 'observations': [{'status': status, 'source': 'Demonstration evidence — not a real stock report',
                    'observed_at': iso(timestamp), 'valid_until': iso(timestamp + timedelta(hours=6)), 'confidence': 'LOW'}], **provenance}))
        db.add(Record(id='demo-source', kind='source', data={'name': 'Demonstration fixtures',
            'last_success': iso(timestamp), 'status': 'DEMO', 'permission': 'Synthetic data'}))
