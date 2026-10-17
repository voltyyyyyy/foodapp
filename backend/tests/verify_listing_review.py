"""Publish the reviewed research batch in a disposable database, never live data."""
import copy
import importlib.util
import json
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
test_root = ROOT / 'backend/data/acceptance'
test_root.mkdir(parents=True, exist_ok=True)
temporary = tempfile.TemporaryDirectory(prefix='listing-review-', dir=test_root)
os.environ['DATABASE_URL'] = 'sqlite:///' + (Path(temporary.name) / 'test.db').as_posix()
os.environ['DEMO_DATA'] = 'false'
os.environ['ENVIRONMENT'] = 'development'
for name in ('ADMIN_EMAIL', 'ADMIN_USERNAME', 'ADMIN_PASSWORD'):
    os.environ.pop(name, None)
sys.path.insert(0, str(ROOT / 'backend'))
spec = importlib.util.spec_from_file_location('review_batch', ROOT / 'tools/apply-listing-review.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

from fastapi.testclient import TestClient
from app.main import app
from app.auth import administrator, session_account
from app.db import Session, Record, engine

# Auth enforcement is covered by verify_staff_portal.py. This suite tests batch
# transactions without creating credentials or sending email.
app.dependency_overrides[administrator] = lambda: 'isolated-review-operator'
app.dependency_overrides[session_account] = lambda: {'id': 'isolated-reader', 'role': 'REGISTERED_USER'}
plan = json.loads((ROOT / 'backend/seeds/listing-review-2026-10-18.json').read_text(encoding='utf-8'))
changes = {r['id']: r for r in plan['updates']}


class LocalAPI:
    def call(self, path, method='GET', body=None):
        response = client.request(method, '/api/' + path, json=body)
        assert response.status_code < 300, (method, path, response.status_code, response.text)
        return response.json()


try:
    with TestClient(app) as client:
        with Session.begin() as db:
            for filename in ('foodconnect-providers.json', 'verified-services.json', 'foodconnect-map-services.json'):
                seed = json.loads((ROOT / 'backend/seeds' / filename).read_text(encoding='utf-8'))
                for raw in seed['records']:
                    kind = raw.get('kind', 'service')
                    data = {k: v for k, v in raw.items() if k not in ('id', 'kind')}
                    data.setdefault('source', seed['source'])
                    data.setdefault('imported_at', plan['reviewed_at'])
                    data.setdefault('verified_at', plan['reviewed_at'])
                    # Force every reviewed update to run while retaining realistic
                    # archived source facts and valid original listing fields.
                    if raw['id'] in changes:
                        data['description'] = 'Previous website review; requires the proposed source update.'
                    if kind == 'provider':
                        data['directory_facts'].pop('reviewed_programmes', None)
                        data['directory_facts'].pop('research_review', None)
                    db.add(Record(id=raw['id'], kind=kind, state='APPROVED',
                                  version=changes.get(raw['id'], {}).get('version', 1), data=data))
        api = LocalAPI()
        receipt = module.execute(plan, api, Path(temporary.name) / 'receipt.json')
        assert receipt['complete'] and receipt['public_counts'] == {'providers': 29, 'services': 7}
        assert len(api.call('admin/quarantine')) == 0 and len(api.call('admin/duplicates')) == 0
        assert len([e for e in receipt['events'] if e['action'].startswith('UPDATE')]) == 24
        first_audit = api.call('admin/audit')
        again = module.execute(plan, api, Path(temporary.name) / 'again.json')
        assert again['complete'] and not again['events'] and api.call('admin/audit') == first_audit

        altered = copy.deepcopy(plan)
        altered['updates'][0]['proposed']['description'] = 'Different unreviewed change'
        try:
            module.execute(altered, api, Path(temporary.name) / 'stale.json')
            raise AssertionError('Stale change was accepted')
        except ValueError as error:
            assert 'Stale reviewed version' in str(error)
        assert api.call('admin/audit') == first_audit

        imports = copy.deepcopy(plan)
        imports['updates'] = []
        imports['imports'][0]['records'][0]['description'] = 'Unexpected collision'
        try:
            module.execute(imports, api, Path(temporary.name) / 'collision.json')
            raise AssertionError('Existing import was overwritten')
        except ValueError as error:
            assert 'overwrite a different record' in str(error)
        assert api.call('admin/audit') == first_audit

        sbl = client.get('/api/services/singapore-buddhist-lodge-meals').json()
        assert len(sbl['distribution_schedules']) == 3 and sbl['diets'] == ['Vegetarian']
        assert sbl['availability'] == 'UNKNOWN' and sbl['observations'] == [] and sbl['wheelchair'] is None
        filos = next(p for p in api.call('providers')['providers'] if p['id'] == 'foodconnect-filos-community-services')
        assert 'programmes' in filos['directory_facts'] and 'reviewed_programmes' in filos['directory_facts']
        print('PASS: 24 audited updates, 7 providers, 5 meal sites and 4 organisations; no quarantines or duplicates.')
        print('PASS: idempotent replay, stale-edit and ID-collision rejection, archived facts, unknown stock and wheelchair access.')
finally:
    engine.dispose()
    temporary.cleanup()
