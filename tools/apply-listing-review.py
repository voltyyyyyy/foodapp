"""Validate a reviewed listing batch, then publish it through audited staff APIs.

Run with backend/.venv/Scripts/python.exe. Validation is the default; --apply
requires the configured staff email/password files. No direct database writes.
"""
import argparse
import copy
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
from app.domain import Provider, Service  # noqa: E402


def merge(base, patch):
    result = copy.deepcopy(base)
    for key, value in patch.items():
        result[key] = merge(result[key], value) if isinstance(value, dict) and isinstance(result.get(key), dict) else copy.deepcopy(value)
    return result


def proposal(record, change):
    values = dict(change.get('proposed', {}))
    if 'directory_facts_patch' in change:
        values['directory_facts'] = merge(record.get('directory_facts', {}), change['directory_facts_patch'])
    return values


def matches(record, values):
    def equal(key, before, after):
        if key in ('verified_at', 'imported_at') and isinstance(before, str) and isinstance(after, str):
            return datetime.fromisoformat(before.replace('Z', '+00:00')) == datetime.fromisoformat(after.replace('Z', '+00:00'))
        return before == after
    return all(equal(key, record.get(key), value) for key, value in values.items())


def validate_record(record, kind):
    if kind == 'organisation':
        if not record.get('name', '').strip() or len(record['name']) > 200:
            raise ValueError('Organisation needs a valid name')
        return
    model = Provider if kind == 'provider' else Service
    model.model_validate(record)
    if datetime.fromisoformat(record['verified_at'].replace('Z', '+00:00')) > datetime.now(timezone.utc):
        raise ValueError(f"Future verification time: {record.get('id', record.get('name'))}")


def validate_plan(plan, snapshot):
    if not plan.get('reviewed_at') or not plan.get('method'):
        raise ValueError('The batch needs its review date and method')
    rows = {r['id']: r for values in snapshot.values() for r in values}
    ids = set()
    for change in plan['updates']:
        if change['collection'] not in ('providers', 'services', 'organisations'):
            raise ValueError('Unsupported update collection')
        record = rows[change['id']]
        values = proposal(record, change)
        if not matches(record, values) and record['version'] != change['version']:
            raise ValueError(f"Stale reviewed version for {change['id']}; review it again")
        if not change.get('reason', '').strip() or len(change['reason']) > 1000:
            raise ValueError('Every update needs a concise publication reason')
        validate_record({**record, **values}, record['kind'])
    for batch in plan['imports']:
        if not batch['source'].startswith(('https://', 'http://')) or not batch['permission'].strip():
            raise ValueError('Every import needs its public source and review scope')
        for raw in batch['records']:
            if not isinstance(raw.get('id'), str) or not 1 <= len(raw['id']) <= 64 or raw['id'] in ids:
                raise ValueError('Missing, duplicate or overlong imported ID')
            ids.add(raw['id'])
            record = {**raw, 'source': batch['source'], 'imported_at': plan['reviewed_at']}
            validate_record(record, raw.get('kind', 'service'))
            if raw['id'] in rows:
                compared = {k: v for k, v in record.items() if k not in ('kind', 'imported_at')}
                if not matches(rows[raw['id']], compared):
                    raise ValueError(f"Import would overwrite a different record: {raw['id']}")
    return rows


class API:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.token = None

    def call(self, path, method='GET', body=None):
        data = None if body is None else json.dumps(body).encode('utf-8')
        headers = {'Content-Type': 'application/json; charset=utf-8'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        try:
            with urlopen(Request(self.base + '/api/' + path, data=data, headers=headers, method=method), timeout=45) as response:
                return json.load(response)
        except HTTPError as error:
            # Do not include request bodies, authentication values or credentials.
            raise RuntimeError(f'{method} {path} returned HTTP {error.code}; inspect the staff portal before retrying') from None


def execute(plan, api, receipt_path):
    snapshot = {c: api.call('admin/' + c) for c in ('providers', 'services', 'organisations')}
    rows = validate_plan(plan, snapshot)
    receipt = {'reviewed_at': plan['reviewed_at'], 'events': [], 'complete': False}

    def record_event(action, target):
        receipt['events'].append({'action': action, 'target': target})
        receipt_path.parent.mkdir(parents=True, exist_ok=True)
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')

    # Stage every import before publishing any record from this run.
    for batch in plan['imports']:
        missing = [r for r in batch['records'] if r['id'] not in rows]
        if missing:
            result = api.call('admin/import', 'POST', {**batch, 'records': missing})
            record_event('IMPORT', batch['source'])
            if result != {'accepted_pending': len(missing), 'quarantined': 0, 'suspected_duplicates': 0}:
                raise RuntimeError('Import needs staff review. No new records were published by this run; check quarantine and duplicates')
    planned_ids = {r['id'] for batch in plan['imports'] for r in batch['records']}
    duplicates = api.call('admin/duplicates')
    if any(r['state'] == 'PENDING' and r.get('candidate_id') in planned_ids for r in duplicates):
        raise RuntimeError('Resolve suspected duplicates in the staff portal before publishing')

    all_new = [r for batch in plan['imports'] for r in batch['records']]
    for kind in ('organisation', 'provider', 'service'):
        collection = {'organisation': 'organisations', 'provider': 'providers', 'service': 'services'}[kind]
        for raw in all_new:
            if raw.get('kind', 'service') != kind:
                continue
            path = 'admin/' + collection + '/' + quote(raw['id'], safe='')
            current = api.call(path)
            if current['state'] == 'PENDING':
                api.call(path + '/publish', 'POST', {})
                record_event({'organisation': 'PUBLISH_ORGANISATION', 'provider': 'PUBLISH_PROVIDER', 'service': 'PUBLISH_IMPORT'}[kind], raw['id'])
            elif current['state'] != 'APPROVED':
                raise RuntimeError(f"Record needs staff review: {raw['id']}")
    for change in plan['updates']:
        path = 'admin/' + change['collection'] + '/' + quote(change['id'], safe='')
        current = api.call(path)
        values = proposal(current, change)
        if matches(current, values):
            continue
        if current['version'] != change['version']:
            raise RuntimeError(f"Record changed since review: {change['id']}")
        api.call(path, 'PATCH', {'version': current['version'], 'proposed': values, 'reason': change['reason']})
        record_event('UPDATE_PROVIDER' if change['collection'] == 'providers' else 'UPDATE_AND_PUBLISH', change['id'])

    public = {c: api.call(c)[c] for c in ('providers', 'services')}
    public_ids = {r['id'] for values in public.values() for r in values}
    expected_public = {r['id'] for r in all_new if r.get('kind', 'service') != 'organisation'}
    if not expected_public <= public_ids:
        raise RuntimeError('An imported listing is missing from the authenticated discovery API')
    # Ensure updates survived validation/serialization and every publication was audited.
    for change in plan['updates']:
        current = api.call('admin/' + change['collection'] + '/' + quote(change['id'], safe=''))
        if not matches(current, proposal(current, change)):
            raise RuntimeError(f"Published values do not match the review: {change['id']}")
    audit = api.call('admin/audit')
    if any(not any(a['action'] == event['action'] and
                   (a.get('detail', {}).get('name') == event['target'] if event['action'] == 'IMPORT' else a['target'] == event['target'])
                   for a in audit) for event in receipt['events']):
        raise RuntimeError('A publication audit entry could not be verified')
    receipt['complete'] = True
    receipt['public_counts'] = {c: len(values) for c, values in public.items()}
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('batch', type=Path)
    parser.add_argument('--snapshot', type=Path, help='Read-only baseline export for offline validation')
    parser.add_argument('--apply', action='store_true', help='Publish the reviewed batch through audited APIs')
    parser.add_argument('--api-url', default='http://localhost:8000')
    parser.add_argument('--email-file', type=Path, default=ROOT / 'backend/data/local-staff-email.txt')
    parser.add_argument('--password-file', type=Path, default=ROOT / 'backend/data/local-staff-password.txt')
    parser.add_argument('--receipt', type=Path, default=ROOT / 'backend/data/listing-review-receipt.json')
    args = parser.parse_args()
    plan = json.loads(args.batch.read_text(encoding='utf-8'))
    if not args.apply:
        if not args.snapshot:
            parser.error('Offline validation requires --snapshot; publication requires --apply')
        validate_plan(plan, json.loads(args.snapshot.read_text(encoding='utf-8')))
        print(f"Validated {len(plan['updates'])} updates and {sum(len(b['records']) for b in plan['imports'])} new records; no live changes.")
        return
    api = API(args.api_url)
    try:
        login = api.call('admin/login', 'POST', {'email': args.email_file.read_text().strip(), 'password': args.password_file.read_text().strip()})
        api.token = login['token']
        receipt = execute(plan, api, args.receipt)
        print(f"Published and verified {len(receipt['events'])} audited operations. Public counts: {receipt['public_counts']}")
    finally:
        if api.token:
            api.call('admin/logout', 'POST', {})
            api.token = None


if __name__ == '__main__':
    main()
