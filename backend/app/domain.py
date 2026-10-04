import math
from datetime import datetime, timezone
from typing import Literal
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field, field_validator, model_validator

SG = ZoneInfo('Asia/Singapore')

AccessMethod = Literal['referral_required', 'application_required', 'provider_assessment', 'contact_to_confirm', 'unverified']
ProviderLocationType = Literal['confirmed_collection_site', 'coordination_site', 'no_confirmed_collection_site']
ServiceLocationRole = Literal['collection_site', 'coordination_site']

class Origin(BaseModel):
    latitude: float = Field(ge=1.13, le=1.48, allow_inf_nan=False)
    longitude: float = Field(ge=103.59, le=104.1, allow_inf_nan=False)

class Search(Origin):
    radius_km: float = Field(default=80, gt=0, le=80, allow_inf_nan=False)
    diets: list[Literal['Halal', 'Vegetarian', 'Vegan', 'Gluten-free', 'Nut-free']] = []
    support_type: str = 'All'
    preferred_time: datetime | None = None
    open_only: bool = False
    wheelchair: bool = False
    offset: int = Field(default=0, ge=0)
    limit: int = Field(default=10, ge=1, le=100)

    @field_validator('preferred_time')
    @classmethod
    def timezone_required(cls, value):
        if value is not None and value.tzinfo is None:
            raise ValueError('Include a timezone in the preferred time')
        return value

class Schedule(BaseModel):
    days: list[int] = Field(min_length=1, max_length=7)
    start: str
    end: str
    label: str = 'Distribution'

    @field_validator('days')
    @classmethod
    def days_valid(cls, value):
        if any(x < 0 or x > 6 for x in value):
            raise ValueError('Weekdays must be 0 (Monday) to 6 (Sunday)')
        return value

    @field_validator('start', 'end')
    @classmethod
    def time_valid(cls, value):
        datetime.strptime(value, '%H:%M')
        return value

    @model_validator(mode='after')
    def interval_valid(self):
        if self.start == self.end:
            raise ValueError('Start and end must differ')
        return self

class Observation(BaseModel):
    status: Literal['AVAILABLE', 'LIMITED', 'UNAVAILABLE', 'UNKNOWN', 'EXPIRED']
    source: str = Field(min_length=1, max_length=300)
    observed_at: datetime
    valid_until: datetime
    confidence: Literal['HIGH', 'MEDIUM', 'LOW'] | None = None
    original_status: str | None = None

    @model_validator(mode='after')
    def validity(self):
        if self.observed_at.tzinfo is None or self.valid_until.tzinfo is None:
            raise ValueError('Evidence times require timezone offsets')
        if self.valid_until <= self.observed_at:
            raise ValueError('Validity must end after observation')
        if self.observed_at > datetime.now(timezone.utc):
            raise ValueError('Observation cannot be in the future')
        return self

class Service(Origin):
    organisation_id: str = Field(min_length=1, max_length=64)
    provider_id: str | None = Field(default=None, min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=200)
    support_type: Literal['Hot meals', 'Groceries', 'Food bank', 'Delivery']
    address: str = Field(min_length=1, max_length=400)
    postal: str = Field(pattern=r'^\d{6}$')
    description: str = Field(max_length=2000)
    eligibility: str = Field(max_length=1000)
    delivery_information: str = Field(default='', max_length=2000)
    diets: list[str] = []
    wheelchair: bool | None = None
    phone: str | None = None
    website: str | None = None
    schedules: list[Schedule] = []
    exceptions: dict[str, list[Schedule]] = {}
    observations: list[Observation] = []
    source: str = Field(min_length=1, max_length=300)
    imported_at: datetime
    verified_at: datetime
    demo: bool = False
    active: bool = True
    access_method: AccessMethod = 'contact_to_confirm'
    location_role: ServiceLocationRole | None = None

    @field_validator('website')
    @classmethod
    def safe_website(cls, value):
        if value and not value.startswith(('https://', 'http://')):
            raise ValueError('Website must be HTTP or HTTPS')
        return value

    @field_validator('phone')
    @classmethod
    def safe_phone(cls, value):
        import re
        if value and not re.fullmatch(r'\+?[0-9 ()-]{8,25}', value):
            raise ValueError('Enter a valid approved telephone number')
        return value

    @model_validator(mode='after')
    def confidence_required(self):
        if self.support_type == 'Food bank' and any(o.confidence is None for o in self.observations):
            raise ValueError('Food-bank availability evidence requires confidence')
        if self.imported_at.tzinfo is None or self.verified_at.tzinfo is None:
            raise ValueError('Provenance times require timezone offsets')
        return self

class Provider(BaseModel):
    """Directory contacts, including referral-only support with no collection location."""
    name: str = Field(min_length=1, max_length=200)
    support_types: list[str] = Field(default_factory=list, max_length=20)
    description: str = Field(max_length=2000)
    eligibility: str = Field(default='', max_length=2000)
    phone: str | None = None
    email: str | None = Field(default=None, max_length=254)
    website: str | None = None
    address: str | None = Field(default=None, max_length=400)
    postal: str | None = Field(default=None, pattern=r'^\d{6}$')
    latitude: float | None = Field(default=None, ge=1.13, le=1.48, allow_inf_nan=False)
    longitude: float | None = Field(default=None, ge=103.59, le=104.1, allow_inf_nan=False)
    source: str = Field(min_length=1, max_length=300)
    provider_source: str | None = None
    contact_source: str | None = None
    directory_facts: dict = Field(default_factory=dict)
    verification_status: Literal['provider_reviewed', 'directory_only'] = 'directory_only'
    location_notes: str | None = Field(default=None, max_length=1000)
    location_type: ProviderLocationType = 'no_confirmed_collection_site'
    access_method: AccessMethod = 'contact_to_confirm'
    verified_at: datetime
    imported_at: datetime
    verification_notes: str = Field(max_length=3000)
    demo: bool = False
    active: bool = True

    @field_validator('website', 'source', 'provider_source', 'contact_source')
    @classmethod
    def safe_url(cls, value):
        if value and not value.startswith(('https://', 'http://')):
            raise ValueError('Directory links must use HTTP or HTTPS')
        return value

    @field_validator('phone')
    @classmethod
    def safe_phone(cls, value):
        return Service.safe_phone(value)

    @field_validator('email')
    @classmethod
    def safe_email(cls, value):
        import re
        if value and not re.fullmatch(r'[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+', value):
            raise ValueError('Enter a public contact email address')
        return value

    @model_validator(mode='after')
    def provenance(self):
        if self.verified_at.tzinfo is None or self.imported_at.tzinfo is None:
            raise ValueError('Review/import times require timezone offsets')
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError('Provide both coordinates or neither')
        return self

class Report(BaseModel):
    service_id: str
    target_kind: Literal['service', 'provider'] = 'service'
    category: Literal['Address', 'Contact', 'Hours', 'Service details', 'Permanently closed', 'Food unavailable', 'Other']
    description: str = Field(min_length=1, max_length=1000)
    evidence: str = Field(default='', max_length=1000)

    @field_validator('description')
    @classmethod
    def trim_description(cls, value):
        if not value.strip():
            raise ValueError('Describe the correction in 1 to 1000 characters')
        return value.strip()

    @field_validator('evidence')
    @classmethod
    def evidence_url(cls, value):
        if value and not value.startswith(('https://', 'http://')):
            raise ValueError('Evidence must be a website link')
        return value

def distance(a, b):
    la1, la2 = math.radians(a['latitude']), math.radians(b['latitude'])
    dlat = la2 - la1
    dlon = math.radians(b['longitude'] - a['longitude'])
    h = math.sin(dlat / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlon / 2) ** 2
    return 6371 * 2 * math.asin(min(1, math.sqrt(h)))

def operating(data, instant):
    local = instant.astimezone(SG)
    key = local.strftime('%Y-%m-%d')
    schedules = data.get('exceptions', {}).get(key, data.get('schedules', []))
    if not schedules and key not in data.get('exceptions', {}):
        return 'UNKNOWN'
    for schedule in schedules:
        minute = local.strftime('%H:%M')
        start, end = schedule['start'], schedule['end']
        if start < end:
            if local.weekday() in schedule['days'] and start <= minute < end:
                return 'OPEN'
        else:
            if (local.weekday() in schedule['days'] and minute >= start) or ((local.weekday() - 1) % 7 in schedule['days'] and minute < end):
                return 'OPEN'
    return 'CLOSED'

def public_service(record, instant=None):
    instant = instant or datetime.now(timezone.utc)
    data = dict(record.data)
    observations = data.get('observations', [])
    eligible = [o for o in observations if datetime.fromisoformat(o['observed_at']) <= instant]
    evidence = max(eligible, key=lambda o: o['observed_at'], default=None)
    status = evidence['status'] if evidence else 'UNKNOWN'
    if evidence and datetime.fromisoformat(evidence['valid_until']) <= instant:
        status = 'EXPIRED'
    data.update(id=record.id, version=record.version, operating_status=operating(data, instant),
        availability='UNKNOWN' if status == 'EXPIRED' else status, evidence=evidence)
    return data
