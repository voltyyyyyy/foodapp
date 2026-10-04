import os
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy import JSON, DateTime, Integer, String, Text, create_engine, event
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
    email: Mapped[str] = mapped_column(String(200), primary_key=True)
    password_hash: Mapped[str] = mapped_column(Text)

class Token(Base):
    __tablename__ = 'sessions'
    digest: Mapped[str] = mapped_column(String(64), primary_key=True)
    email: Mapped[str] = mapped_column(String(200))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

class Audit(Base):
    __tablename__ = 'audit'
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    actor: Mapped[str] = mapped_column(String(200))
    action: Mapped[str] = mapped_column(String(60))
    target: Mapped[str] = mapped_column(String(64), index=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    detail: Mapped[dict] = mapped_column(JSON)
