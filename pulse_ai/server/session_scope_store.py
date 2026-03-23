"""
Maps (app_name, user_id, session_id) -> project_id for multi-tenant AI sessions.

Uses the same DB URL as ADK when SESSION_DB_URL is set; otherwise an in-memory dict.
"""

from __future__ import annotations

import time
from abc import ABC, abstractmethod
from typing import Any

from sqlalchemy import Float, Integer, String, UniqueConstraint, delete, select
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from pulse_ai.constants import (
    SESSION_SCOPE_APP_NAME_LEN,
    SESSION_SCOPE_PROJECT_ID_LEN,
    SESSION_SCOPE_SESSION_ID_LEN,
    SESSION_SCOPE_USER_ID_LEN,
)


class Base(DeclarativeBase):
    pass


class PulseSessionScopeRow(Base):
    """Sidecar table; separate from ADK schema."""

    __tablename__ = "pulse_ai_session_scope"
    __table_args__ = (
        UniqueConstraint(
            "app_name",
            "user_id",
            "session_id",
            name="uq_pulse_ai_session_scope_natural",
        ),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    app_name: Mapped[str] = mapped_column(String(SESSION_SCOPE_APP_NAME_LEN), nullable=False)
    user_id: Mapped[str] = mapped_column(String(SESSION_SCOPE_USER_ID_LEN), nullable=False)
    session_id: Mapped[str] = mapped_column(String(SESSION_SCOPE_SESSION_ID_LEN), nullable=False)
    project_id: Mapped[str] = mapped_column(String(SESSION_SCOPE_PROJECT_ID_LEN), nullable=False)
    updated_at: Mapped[float] = mapped_column(Float, nullable=False)


class SessionScopeStore(ABC):
    @abstractmethod
    async def upsert(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        project_id: str,
    ) -> None:
        pass

    @abstractmethod
    async def get_project_id(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> str | None:
        pass

    @abstractmethod
    async def list_session_ids_for_user_project(
        self,
        *,
        app_name: str,
        user_id: str,
        project_id: str,
    ) -> list[str]:
        """Session ids for this user+project, newest `updated_at` first."""
        pass

    @abstractmethod
    async def delete(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> None:
        """Idempotent: no-op if row missing."""
        pass


class MemorySessionScopeStore(SessionScopeStore):
    def __init__(self) -> None:
        # key -> {"project_id": str, "updated_at": float}
        self._rows: dict[tuple[str, str, str], dict[str, Any]] = {}

    async def upsert(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        project_id: str,
    ) -> None:
        key = (app_name, user_id, session_id)
        self._rows[key] = {"project_id": project_id, "updated_at": time.time()}

    async def get_project_id(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> str | None:
        row = self._rows.get((app_name, user_id, session_id))
        return row["project_id"] if row else None

    async def list_session_ids_for_user_project(
        self,
        *,
        app_name: str,
        user_id: str,
        project_id: str,
    ) -> list[str]:
        matches: list[tuple[float, str]] = []
        for (a, u, sid), data in self._rows.items():
            if a == app_name and u == user_id and data["project_id"] == project_id:
                matches.append((data["updated_at"], sid))
        matches.sort(key=lambda x: x[0], reverse=True)
        return [sid for _, sid in matches]

    async def delete(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> None:
        self._rows.pop((app_name, user_id, session_id), None)


class SqlSessionScopeStore(SessionScopeStore):
    def __init__(self, db_url: str) -> None:
        self._engine: AsyncEngine = create_async_engine(db_url, echo=False)
        self._session_factory = async_sessionmaker(
            self._engine,
            expire_on_commit=False,
        )
        self._initialized = False

    async def _ensure_tables(self) -> None:
        if self._initialized:
            return
        async with self._engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        self._initialized = True

    async def upsert(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        project_id: str,
    ) -> None:
        await self._ensure_tables()
        now = time.time()
        async with self._session_factory() as session:
            stmt = select(PulseSessionScopeRow).where(
                PulseSessionScopeRow.app_name == app_name,
                PulseSessionScopeRow.user_id == user_id,
                PulseSessionScopeRow.session_id == session_id,
            )
            result = await session.execute(stmt)
            existing = result.scalar_one_or_none()
            if existing:
                existing.project_id = project_id
                existing.updated_at = now
            else:
                session.add(
                    PulseSessionScopeRow(
                        app_name=app_name,
                        user_id=user_id,
                        session_id=session_id,
                        project_id=project_id,
                        updated_at=now,
                    )
                )
            await session.commit()

    async def get_project_id(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> str | None:
        await self._ensure_tables()
        async with self._session_factory() as session:
            stmt = select(PulseSessionScopeRow).where(
                PulseSessionScopeRow.app_name == app_name,
                PulseSessionScopeRow.user_id == user_id,
                PulseSessionScopeRow.session_id == session_id,
            )
            result = await session.execute(stmt)
            row = result.scalar_one_or_none()
            return row.project_id if row else None

    async def list_session_ids_for_user_project(
        self,
        *,
        app_name: str,
        user_id: str,
        project_id: str,
    ) -> list[str]:
        await self._ensure_tables()
        async with self._session_factory() as session:
            stmt = (
                select(PulseSessionScopeRow.session_id)
                .where(
                    PulseSessionScopeRow.app_name == app_name,
                    PulseSessionScopeRow.user_id == user_id,
                    PulseSessionScopeRow.project_id == project_id,
                )
                .order_by(PulseSessionScopeRow.updated_at.desc())
            )
            result = await session.execute(stmt)
            return list(result.scalars().all())

    async def delete(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> None:
        await self._ensure_tables()
        async with self._session_factory() as session:
            await session.execute(
                delete(PulseSessionScopeRow).where(
                    PulseSessionScopeRow.app_name == app_name,
                    PulseSessionScopeRow.user_id == user_id,
                    PulseSessionScopeRow.session_id == session_id,
                )
            )
            await session.commit()


def _to_async_sqlalchemy_url(url: str) -> str:
    """Map sync-style ADK URLs to SQLAlchemy async drivers."""
    u = url.strip()
    if "+aiosqlite" in u or "+asyncpg" in u:
        return u
    if u.startswith("sqlite:"):
        return "sqlite+aiosqlite" + u[len("sqlite") :]
    if u.startswith("postgresql://"):
        return "postgresql+asyncpg://" + u[len("postgresql://") :]
    if u.startswith("postgres://"):
        return "postgresql+asyncpg://" + u[len("postgres://") :]
    return u


def create_session_scope_store(session_db_url: str | None) -> SessionScopeStore:
    """SQL sidecar when SESSION_DB_URL is set; else in-memory."""
    if session_db_url and session_db_url.strip():
        return SqlSessionScopeStore(_to_async_sqlalchemy_url(session_db_url.strip()))
    return MemorySessionScopeStore()
