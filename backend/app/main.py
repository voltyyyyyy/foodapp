import asyncio
import hashlib
import math
import os
import secrets
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from uuid import uuid4

from argon2 import PasswordHasher
from argon2.exceptions import VerificationError
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field, ValidationError
from sqlalchemy import func, select, text, update

from .db import Admin, Audit, Base, Record, Session, Token, engine, iso, now
from .domain import Origin, Provider, Report, Search, Service, distance, public_service
from .onemap import adapter

hasher = PasswordHasher()
security = HTTPBearer(auto_error=False)
rates = defaultdict(deque)
rate_salt = secrets.token_hex(32)
dummy_hash = hasher.hash(secrets.token_urlsafe(32))

@asynccontextmanager
async def lifespan(_):
    if os.getenv('ENVIRONMENT') == 'production' and engine.dialect.name == 'sqlite':
        raise RuntimeError('Production requires PostgreSQL')
    Base.metadata.create_all(engine)
    email, password = os.getenv('ADMIN_EMAIL'), os.getenv('ADMIN_PASSWORD')
    if email and password:
        if len(password) < 12:
            raise RuntimeError('ADMIN_PASSWORD must be at least 12 characters')
        with Session.begin() as db:
            if not db.get(Admin, email.lower()):
                db.add(Admin(email=email.lower(), password_hash=hasher.hash(password)))
    if os.getenv('DEMO_DATA', 'false').lower() == 'true':
        from .seed import seed
        seed()
    yield

app = FastAPI(title='FoodBridge Navigator', version='1.0.0', lifespan=lifespan)

@app.middleware('http')
async def limits(request: Request, call_next):
    from starlette.responses import JSONResponse
    length = request.headers.get('content-length', '0')
    if length.isdigit() and int(length) > 2_000_000:
        return JSONResponse({'detail': 'Request is too large'}, status_code=413)
    started = time.monotonic()
    response = await call_next(request)
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['Cache-Control'] = 'no-store'
    response.headers['Server-Timing'] = f'api;dur={(time.monotonic() - started) * 1000:.1f}'
    return response

def throttle(request, action, limit):
    # Short-lived salted hashes avoid retaining raw IP addresses.
    address = request.client.host if request.client else 'unknown'
    key = hashlib.sha256(f'{rate_salt}:{action}:{address}:{int(time.time() // 3600)}'.encode()).hexdigest()
    instant = time.monotonic()
    for expired in [k for k, v in rates.items() if not v or v[-1] < instant - 300]:
        del rates[expired]
    history = rates[key]
    while history and history[0] < instant - 300:
        history.popleft()
    if len(history) >= limit:
        raise HTTPException(429, 'Too many attempts. Please try again in a few minutes.')
    history.append(instant)

def administrator(credentials: HTTPAuthorizationCredentials | None = Depends(security)):
    if not credentials:
        raise HTTPException(401, 'Staff sign-in required')
    digest = hashlib.sha256(credentials.credentials.encode()).hexdigest()
    with Session() as db:
        session = db.get(Token, digest)
        if not session or datetime.fromisoformat(iso(session.expires_at)) <= now():
            raise HTTPException(401, 'Your staff session has expired')
        return session.email

def item(db, id_, kind=None, approved=False):
    record = db.get(Record, id_)
    if not record or (kind and record.kind != kind) or (approved and record.state != 'APPROVED'):
        raise HTTPException(404, 'Record not found')
    return record

def serial(record):
    return {'id': record.id, 'kind': record.kind, 'state': record.state, 'version': record.version,
        'created_at': iso(record.created_at), 'updated_at': iso(record.updated_at), **record.data}

def audit(db, actor, action, target, detail):
    db.add(Audit(id=str(uuid4()), actor=actor, action=action, target=target, detail=detail))

def latest_update(db):
    timestamp = db.scalar(select(func.max(Record.updated_at)).where(Record.kind == 'service', Record.state == 'APPROVED'))
    return iso(timestamp) if timestamp else None

def with_organisation_names(db, values):
    ids = {v['organisation_id'] for v in values}
    names = {r.id: r.data['name'] for r in db.scalars(select(Record).where(Record.kind == 'organisation', Record.id.in_(ids)))} if ids else {}
    for value in values:
        value['organisation_name'] = names.get(value['organisation_id'], 'Organisation name not confirmed')
    return values

@app.get('/health/live')
def live():
    return {'status': 'ok'}

@app.get('/health/ready')
def ready():
    with Session() as db:
        db.execute(text('SELECT 1'))
    return {'status': 'ok'}

@app.get('/api/services')
def services():
    with Session() as db:
        records = db.scalars(select(Record).where(Record.kind == 'service', Record.state == 'APPROVED')).all()
        return {'services': with_organisation_names(db, [public_service(r) for r in records]), 'last_update': latest_update(db)}

@app.get('/api/services/{service_id}')
def details(service_id: str):
    with Session() as db:
        return with_organisation_names(db, [public_service(item(db, service_id, 'service', True))])[0]

@app.get('/api/providers')
def providers():
    with Session() as db:
        records = db.scalars(select(Record).where(Record.kind == 'provider', Record.state == 'APPROVED')).all()
        values = [serial(r) for r in records if r.data.get('active', True)]
        return {'providers': sorted(values, key=lambda value: value['name'].casefold()),
            'last_update': max((iso(r.updated_at) for r in records), default=None)}

@app.post('/api/search')
def search(criteria: Search):
    instant = criteria.preferred_time or now()
    matches = []
    distances = {}
    with Session() as db:
        delta_lat = criteria.radius_km / 110.5
        delta_lon = criteria.radius_km / (110.5 * math.cos(math.radians(criteria.latitude)))
        candidates = select(Record).where(Record.kind == 'service', Record.state == 'APPROVED',
            Record.data['latitude'].as_float().between(criteria.latitude - delta_lat, criteria.latitude + delta_lat),
            Record.data['longitude'].as_float().between(criteria.longitude - delta_lon, criteria.longitude + delta_lon))
        for record in db.scalars(candidates):
            service = public_service(record, instant)
            if not service.get('active', True):
                continue
            km = distance(criteria.model_dump(), service)
            if km > criteria.radius_km or not set(criteria.diets).issubset(service['diets']):
                continue
            if criteria.support_type != 'All' and criteria.support_type != service['support_type']:
                continue
            if criteria.wheelchair and service.get('wheelchair') is not True:
                continue
            if (criteria.open_only or criteria.preferred_time) and service['operating_status'] != 'OPEN':
                continue
            reasons = [f"{service['support_type']} in Singapore" if criteria.radius_km >= 80 else f"{service['support_type']} within your {criteria.radius_km:g} km radius"]
            if criteria.diets:
                reasons.append('Matches all selected dietary needs: ' + ', '.join(criteria.diets))
            if criteria.wheelchair:
                reasons.append('Wheelchair access recorded')
            if service['operating_status'] == 'OPEN':
                reasons.append('Scheduled to be open at your selected time')
            service.update(distance_km=round(km, 2), explanations=reasons, travel_time=None)
            distances[service['id']] = km
            matches.append(service)
        # Filter first, then rank by unrounded geographic distance with deterministic ties.
        matches.sort(key=lambda s: (distances[s['id']], s['id']))
        return {'services': with_organisation_names(db, matches[criteria.offset:criteria.offset + criteria.limit]), 'total': len(matches),
            'last_update': latest_update(db), 'criteria': {'radius_km': criteria.radius_km, 'diets': criteria.diets}}

class Geocode(BaseModel):
    query: str = Field(min_length=3, max_length=200)

@app.post('/api/geocode')
async def geocode(body: Geocode):
    query = body.query.strip()
    if query.isdigit() and len(query) != 6:
        raise HTTPException(422, 'Singapore postal codes contain exactly six digits')
    try:
        candidates = await asyncio.wait_for(adapter.geocode(query), timeout=4)
        if candidates:
            return {'candidates': candidates, 'source': 'OneMap'}
    except Exception:
        pass
    with Session() as db:
        records = db.scalars(select(Record).where(Record.kind == 'service', Record.state == 'APPROVED'))
        candidates = [{'address': r.data['address'], 'postal': r.data['postal'], 'latitude': r.data['latitude'],
            'longitude': r.data['longitude']} for r in records if query.casefold() in (r.data['postal'] + ' ' + r.data['address']).casefold()]
    if candidates:
        return {'candidates': candidates[:10], 'source': 'Cached approved service addresses'}
    raise HTTPException(503, 'Address lookup is unavailable or no Singapore match was found. Try a postal code or device location.')

class Route(Origin):
    service_id: str
    mode: str = 'walk'

@app.post('/api/routes')
async def route(body: Route):
    if body.mode not in ('walk', 'pt', 'drive'):
        raise HTTPException(422, 'Select walking, public transport or driving')
    with Session() as db:
        destination = dict(item(db, body.service_id, 'service', True).data)
    try:
        return await asyncio.wait_for(adapter.route(body.model_dump(), destination, body.mode), timeout=4)
    except Exception:
        return {'available': False, 'message': 'Route unavailable. Service details are still available. Try again or contact the provider.'}

@app.post('/api/reports', status_code=201)
def report(body: Report, request: Request):
    throttle(request, 'report', 15)
    with Session.begin() as db:
        service = item(db, body.service_id, body.target_kind, True)
        reference = 'FB-' + secrets.token_hex(8).upper()
        db.add(Record(id=reference, kind='report', state='PENDING', data={**body.model_dump(),
            'service_name': service.data['name'], 'base_version': service.version}))
    return {'reference': reference, 'state': 'PENDING'}

class Login(BaseModel):
    email: str = Field(max_length=200)
    password: str = Field(max_length=200)

@app.post('/api/admin/login')
def login(body: Login, request: Request):
    throttle(request, 'login', 8)
    with Session.begin() as db:
        admin = db.get(Admin, body.email.lower().strip())
        try:
            hasher.verify(admin.password_hash if admin else dummy_hash, body.password)
        except VerificationError:
            raise HTTPException(401, 'Email or password is incorrect')
        if not admin:
            raise HTTPException(401, 'Email or password is incorrect')
        token = secrets.token_urlsafe(32)
        db.add(Token(digest=hashlib.sha256(token.encode()).hexdigest(), email=admin.email, expires_at=now() + timedelta(hours=8)))
        return {'token': token, 'email': admin.email}

@app.post('/api/admin/logout')
def logout(actor=Depends(administrator), credentials: HTTPAuthorizationCredentials = Depends(security)):
    with Session.begin() as db:
        token = db.get(Token, hashlib.sha256(credentials.credentials.encode()).hexdigest())
        if token:
            db.delete(token)
    return {'status': 'signed_out'}

@app.get('/api/admin/audit')
def audit_entries(actor=Depends(administrator)):
    with Session() as db:
        return [{'id': a.id, 'actor': a.actor, 'action': a.action, 'target': a.target,
            'timestamp': iso(a.timestamp), 'detail': a.detail} for a in db.scalars(select(Audit).order_by(Audit.timestamp.desc()).limit(200))]

@app.get('/api/admin/services')
def admin_services(actor=Depends(administrator)):
    with Session() as db:
        return [serial(r) for r in db.scalars(select(Record).where(Record.kind == 'service'))]

@app.get('/api/admin/{collection}')
def queue(collection: str, actor=Depends(administrator)):
    kinds = {'reports': 'report', 'changes': 'change', 'sources': 'source', 'quarantine': 'quarantine', 'duplicates': 'duplicate', 'operations': 'operation', 'organisations': 'organisation', 'providers': 'provider'}
    if collection not in kinds:
        raise HTTPException(404, 'Collection not found')
    with Session() as db:
        records = db.scalars(select(Record).where(Record.kind == kinds[collection]).order_by(Record.created_at.desc()).limit(500)).all()
        result = []
        for record in records:
            value = serial(record)
            if collection == 'operations' and record.data.get('last_success'):
                last_success = datetime.fromisoformat(record.data['last_success'])
                value['status'] = 'OVERDUE' if now() - last_success > timedelta(hours=24) else record.data['status']
            target = record.data.get('service_id') or record.data.get('target_id')
            if target and db.get(Record, target):
                value['current'] = serial(db.get(Record, target))
            result.append(value)
        return result

class Edit(BaseModel):
    version: int
    proposed: dict

def publish(db, target, proposal, expected_version):
    if target.version != expected_version:
        raise HTTPException(409, 'This record changed. Reload it before making a decision.')
    merged = {**target.data, **proposal}
    if target.kind == 'service':
        try:
            merged = Service.model_validate(merged).model_dump(mode='json')
        except ValidationError as error:
            raise HTTPException(422, str(error))
        item(db, merged['organisation_id'], 'organisation', True)
    elif target.kind == 'provider':
        try:
            merged = Provider.model_validate(merged).model_dump(mode='json')
        except ValidationError as error:
            raise HTTPException(422, str(error))
    else:
        if not merged.get('name') or not merged.get('source'):
            raise HTTPException(422, 'Organisation requires a name and source')
    before = dict(target.data)
    updated = db.execute(update(Record).where(Record.id == target.id, Record.version == expected_version)
        .values(data=merged, version=expected_version + 1, updated_at=now()))
    if updated.rowcount != 1:
        raise HTTPException(409, 'Another administrator changed this record')
    return before, merged

@app.patch('/api/admin/services/{id_}')
def edit_service(id_: str, body: Edit, actor=Depends(administrator)):
    with Session.begin() as db:
        target = item(db, id_, 'service')
        before, after = publish(db, target, body.proposed, body.version)
        audit(db, actor, 'UPDATE_AND_PUBLISH', id_, {'before': before, 'after': after})
    return {'status': 'published'}

class Change(BaseModel):
    target_id: str
    version: int
    proposed: dict
    source: str = Field(min_length=1, max_length=300)

@app.post('/api/admin/changes', status_code=201)
def create_change(body: Change, actor=Depends(administrator)):
    with Session.begin() as db:
        target = item(db, body.target_id)
        if target.kind not in ('service', 'organisation'):
            raise HTTPException(422, 'Select an organisation or service')
        if target.version != body.version:
            raise HTTPException(409, 'Reload the current record')
        id_ = str(uuid4())
        db.add(Record(id=id_, kind='change', state='PENDING', data={**body.model_dump(), 'base_version': body.version}))
        audit(db, actor, 'SUBMIT_CHANGE', id_, {'target': target.id, 'source': body.source})
    return {'id': id_, 'state': 'PENDING'}

class Decision(BaseModel):
    decision: str
    reason: str = Field(default='', max_length=1000)
    proposed: dict = {}
    target_version: int | None = None

@app.post('/api/admin/{collection}/{id_}/decision')
def decide(collection: str, id_: str, body: Decision, actor=Depends(administrator)):
    if collection not in ('reports', 'changes') or body.decision not in ('APPROVED', 'REJECTED'):
        raise HTTPException(422, 'Select approve or reject for a report or change request')
    if body.decision == 'REJECTED' and not body.reason.strip():
        raise HTTPException(422, 'A rejection reason is required')
    with Session.begin() as db:
        record = item(db, id_, 'report' if collection == 'reports' else 'change')
        if record.state != 'PENDING':
            raise HTTPException(409, 'This item has already been decided')
        detail = {'reason': body.reason, 'decision': body.decision}
        if body.decision == 'APPROVED':
            target = item(db, record.data.get('service_id') or record.data['target_id'])
            proposal = body.proposed or record.data.get('proposed', {})
            if not proposal:
                raise HTTPException(422, 'An approved correction must contain validated proposed values')
            if body.target_version is None:
                raise HTTPException(422, 'The reviewed target version is required')
            before, after = publish(db, target, proposal, body.target_version)
            detail.update(before=before, after=after, published_target=target.id)
        new_data = {**record.data, 'reason': body.reason, 'decision_actor': actor, 'decided_at': iso(now()), 'proposed': body.proposed or record.data.get('proposed', {})}
        result = db.execute(update(Record).where(Record.id == id_, Record.state == 'PENDING').values(state=body.decision, data=new_data))
        if result.rowcount != 1:
            raise HTTPException(409, 'Another administrator already decided this item')
        audit(db, actor, body.decision, id_, detail)
    return {'state': body.decision}

class Import(BaseModel):
    source: str = Field(min_length=1, max_length=300)
    permission: str = Field(min_length=1, max_length=1000)
    records: list[dict] = Field(max_length=2000)

@app.post('/api/admin/import')
def import_records(body: Import, actor=Depends(administrator)):
    from difflib import SequenceMatcher
    accepted, quarantined, duplicates = 0, 0, 0
    timestamp, run_id = now(), str(uuid4())
    with Session.begin() as db:
        for raw in sorted(body.records, key=lambda r: r.get('kind') != 'organisation'):
            try:
                if raw.get('kind') == 'provider':
                    id_ = raw.get('id') or str(uuid4())
                    if not isinstance(id_, str) or len(id_) > 64:
                        raise ValueError('Provider identifier must be at most 64 characters')
                    data = Provider.model_validate({**raw, 'source': body.source, 'imported_at': iso(timestamp)}).model_dump(mode='json')
                    if db.get(Record, id_):
                        raise ValueError('Existing providers require a change request')
                    for other in db.scalars(select(Record).where(Record.kind == 'provider')):
                        if SequenceMatcher(None, data['name'].casefold(), other.data['name'].casefold()).ratio() > .9:
                            duplicates += 1
                            db.add(Record(id=str(uuid4()), kind='duplicate', state='PENDING', data={
                                'candidate_id': id_, 'existing_id': other.id, 'target_kind': 'provider', 'name': data['name']}))
                    db.add(Record(id=id_, kind='provider', state='PENDING', data=data))
                    accepted += 1
                    continue
                if raw.get('kind') == 'organisation':
                    id_ = raw.get('id') or str(uuid4())
                    if not isinstance(id_, str) or len(id_) > 64:
                        raise ValueError('Organisation identifier must be at most 64 characters')
                    org_input = Organisation.model_validate({'name': raw.get('name'), 'source': body.source})
                    if db.get(Record, id_):
                        raise ValueError('Existing organisations require a change request')
                    for other in db.scalars(select(Record).where(Record.kind == 'organisation')):
                        if SequenceMatcher(None, org_input.name.casefold(), other.data['name'].casefold()).ratio() > .85:
                            duplicates += 1
                            db.add(Record(id=str(uuid4()), kind='duplicate', state='PENDING', data={
                                'candidate_id': id_, 'existing_id': other.id, 'target_kind': 'organisation',
                                'candidate_organisation': id_, 'existing_organisation': other.id, 'name': org_input.name}))
                    db.add(Record(id=id_, kind='organisation', state='PENDING', data={**org_input.model_dump(),
                        'imported_at': iso(timestamp), 'verified_at': iso(timestamp), 'permission': body.permission}))
                    db.flush()
                    accepted += 1
                    continue
                data = Service.model_validate({**raw, 'source': body.source, 'imported_at': iso(timestamp)}).model_dump(mode='json')
                organisation = item(db, data['organisation_id'], 'organisation')
                id_ = raw.get('id') or str(uuid4())
                if not isinstance(id_, str) or len(id_) > 64:
                    raise ValueError('Record identifier must be a string of at most 64 characters')
                existing = db.get(Record, id_)
                if existing:
                    raise ValueError('Existing records require a change request; imports cannot overwrite approved values')
                for other in db.scalars(select(Record).where(Record.kind == 'service')):
                    if SequenceMatcher(None, data['name'].casefold(), other.data['name'].casefold()).ratio() > .8 and distance(data, other.data) < .3:
                        duplicates += 1
                        db.add(Record(id=str(uuid4()), kind='duplicate', state='PENDING', data={'candidate_id': id_, 'existing_id': other.id,
                            'candidate_organisation': organisation.id, 'existing_organisation': other.data['organisation_id'], 'name': data['name']}))
                db.add(Record(id=id_, kind='service', state='PENDING', data=data))
                accepted += 1
            except (ValidationError, ValueError, HTTPException) as error:
                quarantined += 1
                db.add(Record(id=str(uuid4()), kind='quarantine', state='PENDING', data={
                    'source': body.source, 'reason': str(error), 'raw': raw, 'run_id': run_id}))
        source_id = hashlib.sha256(body.source.encode()).hexdigest()
        source = db.get(Record, source_id)
        source_data = {'name': body.source, 'permission': body.permission, 'last_attempt': iso(timestamp),
            'last_success': iso(timestamp) if accepted else (source.data.get('last_success') if source else None),
            'status': 'HEALTHY' if accepted and not quarantined else 'ATTENTION', 'accepted': accepted, 'quarantined': quarantined, 'run_id': run_id}
        if source:
            source.data = source_data
        else:
            db.add(Record(id=source_id, kind='source', data=source_data))
        audit(db, actor, 'IMPORT', run_id, source_data)
    return {'accepted_pending': accepted, 'quarantined': quarantined, 'suspected_duplicates': duplicates}

@app.post('/api/admin/providers/{id_}/publish')
def approve_provider(id_: str, actor=Depends(administrator)):
    with Session.begin() as db:
        record = item(db, id_, 'provider')
        Provider.model_validate(record.data)
        if any(r.data.get('candidate_id') == id_ for r in db.scalars(select(Record).where(Record.kind == 'duplicate', Record.state == 'PENDING'))):
            raise HTTPException(409, 'Resolve suspected duplicates before publishing')
        result = db.execute(update(Record).where(Record.id == id_, Record.state == 'PENDING').values(state='APPROVED', updated_at=now()))
        if result.rowcount != 1:
            raise HTTPException(409, 'This provider is not pending publication')
        audit(db, actor, 'PUBLISH_PROVIDER', id_, {'after': record.data})
    return {'status': 'published'}

@app.patch('/api/admin/providers/{id_}')
def edit_provider(id_: str, body: Edit, actor=Depends(administrator)):
    with Session.begin() as db:
        target = item(db, id_, 'provider')
        before, after = publish(db, target, body.proposed, body.version)
        audit(db, actor, 'UPDATE_PROVIDER', id_, {'before': before, 'after': after})
    return {'status': 'published'}

@app.post('/api/admin/services/{id_}/publish')
def approve_import(id_: str, actor=Depends(administrator)):
    with Session.begin() as db:
        record = item(db, id_, 'service')
        if record.state != 'PENDING':
            raise HTTPException(409, 'This service is not pending publication')
        Service.model_validate(record.data)
        item(db, record.data['organisation_id'], 'organisation', True)
        if any(r.data.get('candidate_id') == id_ for r in db.scalars(select(Record).where(Record.kind == 'duplicate', Record.state == 'PENDING'))):
            raise HTTPException(409, 'Resolve suspected duplicates before publishing')
        result = db.execute(update(Record).where(Record.id == id_, Record.state == 'PENDING').values(state='APPROVED'))
        if result.rowcount != 1:
            raise HTTPException(409, 'Another administrator already published this service')
        audit(db, actor, 'PUBLISH_IMPORT', id_, {'after': record.data})
    return {'status': 'published'}

@app.post('/api/admin/organisations/{id_}/publish')
def approve_organisation(id_: str, actor=Depends(administrator)):
    with Session.begin() as db:
        record = item(db, id_, 'organisation')
        if any(r.data.get('candidate_id') == id_ for r in db.scalars(select(Record).where(Record.kind == 'duplicate', Record.state == 'PENDING'))):
            raise HTTPException(409, 'Resolve suspected duplicates before publishing')
        result = db.execute(update(Record).where(Record.id == id_, Record.state == 'PENDING').values(state='APPROVED'))
        if result.rowcount != 1:
            raise HTTPException(409, 'This organisation is not pending publication')
        audit(db, actor, 'PUBLISH_ORGANISATION', id_, {'after': record.data})
    return {'status': 'published'}

@app.patch('/api/admin/organisations/{id_}')
def edit_organisation(id_: str, body: Edit, actor=Depends(administrator)):
    with Session.begin() as db:
        target = item(db, id_, 'organisation')
        before, after = publish(db, target, body.proposed, body.version)
        audit(db, actor, 'UPDATE_AND_PUBLISH', id_, {'before': before, 'after': after})
    return {'status': 'published'}

class Organisation(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    source: str = Field(min_length=1, max_length=300)

@app.post('/api/admin/organisations', status_code=201)
def organisation(body: Organisation, actor=Depends(administrator)):
    with Session.begin() as db:
        id_ = str(uuid4())
        data = {**body.model_dump(), 'imported_at': iso(now()), 'verified_at': iso(now())}
        db.add(Record(id=id_, kind='organisation', data=data))
        audit(db, actor, 'CREATE_ORGANISATION', id_, {'after': data})
    return {'id': id_}

@app.post('/api/admin/duplicates/{id_}/resolve')
def duplicate_resolve(id_: str, body: Decision, actor=Depends(administrator)):
    if body.decision not in ('DISTINCT', 'DUPLICATE') or not body.reason.strip():
        raise HTTPException(422, 'Choose DISTINCT or DUPLICATE and provide a reason')
    with Session.begin() as db:
        record = item(db, id_, 'duplicate')
        result = db.execute(update(Record).where(Record.id == id_, Record.state == 'PENDING').values(
            state=body.decision, data={**record.data, 'reason': body.reason, 'decision_actor': actor}))
        if result.rowcount != 1:
            raise HTTPException(409, 'Duplicate review has already been completed')
        if body.decision == 'DUPLICATE':
            candidate = item(db, record.data['candidate_id'], record.data.get('target_kind', 'service'))
            candidate.state = 'REJECTED'
        audit(db, actor, 'RESOLVE_DUPLICATE', id_, {'decision': body.decision, 'reason': body.reason})
    return {'state': body.decision}
