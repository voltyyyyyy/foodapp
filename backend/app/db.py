import os
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import JSON, Boolean, DateTime, Integer, String, Text, ForeignKey, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

def now():
    return datetime.now(timezone.utc)

def iso(value):
    if isinstance(value, str):
        return value
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.isoformat()

DATABASE_URL = os.getenv('DATABASE_URL', 'sqlite:///./data/foodbridge.db')
Path('data').mkdir(exist_ok=True)
engine = create_engine(DATABASE_URL, pool_pre_ping=True,
    connect_args={'check_same_thread': False, 'timeout': 15} if DATABASE_URL.startswith('sqlite') else {})
if DATABASE_URL.startswith('sqlite'):
    @event.listens_for(engine, 'connect')
    def configure_sqlite(connection, _):
        connection.execute('PRAGMA journal_mode=WAL')
        connection.execute('PRAGMA foreign_keys=ON')
Session = sessionmaker(engine, expire_on_commit=False)

class Base(DeclarativeBase):
    pass

class Record(Base):
    __tablename__ = 'records'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    kind: Mapped[str] = mapped_column(String(30), index=True)
    state: Mapped[str] = mapped_column(String(30), default='APPROVED', index=True)
    version: Mapped[int] = mapped_column(Integer, default=1)
    data: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)

class Admin(Base):
    __tablename__ = 'admins'
    # Keep the existing physical column so deployed databases need no destructive migration.
    username: Mapped[str] = mapped_column('email', String(200), primary_key=True)
    password_hash: Mapped[str] = mapped_column(Text)

class Token(Base):
    __tablename__ = 'sessions'
    digest: Mapped[str] = mapped_column(String(64), primary_key=True)
    username: Mapped[str] = mapped_column('email', String(200))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

class Audit(Base):
    __tablename__ = 'audit'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    actor: Mapped[str] = mapped_column(String(200))
    action: Mapped[str] = mapped_column(String(60))
    target: Mapped[str] = mapped_column(String(64), index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    detail: Mapped[dict] = mapped_column(JSON)

# Additive account schema. Existing listing/admin tables and their physical columns stay intact.
class Account(Base):
    __tablename__ = 'accounts'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    email: Mapped[str] = mapped_column(String(254), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(100))
    password_hash: Mapped[str | None] = mapped_column(Text, nullable=True)
    google_subject: Mapped[str | None] = mapped_column(String(255), unique=True, nullable=True)
    role: Mapped[str] = mapped_column(String(30), default='REGISTERED_USER')
    state: Mapped[str] = mapped_column(String(30), default='PENDING_VERIFICATION')
    failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    blocked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)

class AccountSession(Base):
    __tablename__ = 'account_sessions'
    digest: Mapped[str] = mapped_column(String(64), primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey('accounts.id'), index=True)
    issued_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    last_activity: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

class AccountLink(Base):
    __tablename__ = 'account_links'
    digest: Mapped[str] = mapped_column(String(64), primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey('accounts.id'), index=True)
    purpose: Mapped[str] = mapped_column(String(20))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

class GoogleChallenge(Base):
    __tablename__ = 'google_challenges'
    digest: Mapped[str] = mapped_column(String(64), primary_key=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

# Additive push schema. Catalogue events are committed alongside the publication
# and audit entry, so an API crash cannot lose the need to notify devices.
class PushDevice(Base):
    __tablename__ = 'push_devices'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    account_id: Mapped[str] = mapped_column(ForeignKey('accounts.id', ondelete='CASCADE'), index=True)
    token: Mapped[str] = mapped_column(Text)
    token_digest: Mapped[str] = mapped_column(String(64), unique=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)

class CatalogueEvent(Base):
    __tablename__ = 'catalogue_events'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    entity_kind: Mapped[str] = mapped_column(String(30))
    entity_id: Mapped[str] = mapped_column(String(64))
    version: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)
    prepared: Mapped[bool] = mapped_column(Boolean, default=False)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, index=True)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    delivered_count: Mapped[int] = mapped_column(Integer, default=0)
    last_error: Mapped[str | None] = mapped_column(String(100), nullable=True)
    lease_token: Mapped[str | None] = mapped_column(String(64), nullable=True)
    lease_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

class PushDelivery(Base):
    __tablename__ = 'push_deliveries'
    event_id: Mapped[str] = mapped_column(ForeignKey('catalogue_events.id', ondelete='CASCADE'), primary_key=True)
    device_id: Mapped[str] = mapped_column(ForeignKey('push_devices.id', ondelete='CASCADE'), primary_key=True)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, index=True)
    last_error: Mapped[str | None] = mapped_column(String(100), nullable=True)
