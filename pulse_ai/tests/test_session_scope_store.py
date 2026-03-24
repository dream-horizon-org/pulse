"""Tests for pulse_ai_session_scope sidecar store."""

from __future__ import annotations

import tempfile
from pathlib import Path

import pytest

from pulse_ai.server.session_scope_store import (
    MemorySessionScopeStore,
    SqlSessionScopeStore,
    _to_async_sqlalchemy_url,
    create_session_scope_store,
)


@pytest.fixture
def memory_store() -> MemorySessionScopeStore:
    return MemorySessionScopeStore()


@pytest.fixture
async def sql_store() -> SqlSessionScopeStore:
    tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
    tmp.close()
    path = Path(tmp.name)
    url = _to_async_sqlalchemy_url(f"sqlite:///{path}")
    store = SqlSessionScopeStore(url)
    yield store
    await store._engine.dispose()
    path.unlink(missing_ok=True)


class TestToAsyncSqlalchemyUrl:
    def test_sqlite_converts_to_aiosqlite(self) -> None:
        assert _to_async_sqlalchemy_url("sqlite:///foo.db") == "sqlite+aiosqlite:///foo.db"

    def test_already_async_unchanged(self) -> None:
        u = "sqlite+aiosqlite:///x.db"
        assert _to_async_sqlalchemy_url(u) == u


class TestMemorySessionScopeStore:
    @pytest.mark.asyncio
    async def test_upsert_get(self, memory_store: MemorySessionScopeStore) -> None:
        await memory_store.upsert(
            app_name="app",
            user_id="u1",
            session_id="s1",
            project_id="p1",
        )
        assert (
            await memory_store.get_project_id(
                app_name="app",
                user_id="u1",
                session_id="s1",
            )
            == "p1"
        )

    @pytest.mark.asyncio
    async def test_list_filters_and_orders(self, memory_store: MemorySessionScopeStore) -> None:
        await memory_store.upsert(
            app_name="a", user_id="u", session_id="s1", project_id="p1",
        )
        await memory_store.upsert(
            app_name="a", user_id="u", session_id="s2", project_id="p1",
        )
        await memory_store.upsert(
            app_name="a", user_id="u", session_id="s3", project_id="p2",
        )
        ids = await memory_store.list_session_ids_for_user_project(
            app_name="a", user_id="u", project_id="p1",
        )
        assert set(ids) == {"s1", "s2"}

    @pytest.mark.asyncio
    async def test_delete_idempotent(self, memory_store: MemorySessionScopeStore) -> None:
        await memory_store.upsert(
            app_name="a", user_id="u", session_id="s1", project_id="p1",
        )
        await memory_store.delete(app_name="a", user_id="u", session_id="s1")
        assert (
            await memory_store.get_project_id(app_name="a", user_id="u", session_id="s1")
            is None
        )
        await memory_store.delete(app_name="a", user_id="u", session_id="s1")


class TestSqlSessionScopeStore:
    @pytest.mark.asyncio
    async def test_upsert_get(self, sql_store: SqlSessionScopeStore) -> None:
        await sql_store.upsert(
            app_name="app",
            user_id="u1",
            session_id="s1",
            project_id="p1",
        )
        assert (
            await sql_store.get_project_id(
                app_name="app",
                user_id="u1",
                session_id="s1",
            )
            == "p1"
        )

    @pytest.mark.asyncio
    async def test_list_and_delete(self, sql_store: SqlSessionScopeStore) -> None:
        await sql_store.upsert(app_name="a", user_id="u", session_id="s1", project_id="p1")
        await sql_store.upsert(app_name="a", user_id="u", session_id="s2", project_id="p1")
        ids = await sql_store.list_session_ids_for_user_project(
            app_name="a", user_id="u", project_id="p1",
        )
        assert set(ids) == {"s1", "s2"}
        await sql_store.delete(app_name="a", user_id="u", session_id="s1")
        ids2 = await sql_store.list_session_ids_for_user_project(
            app_name="a", user_id="u", project_id="p1",
        )
        assert ids2 == ["s2"]


def test_create_session_scope_store_memory_when_empty() -> None:
    store = create_session_scope_store(None)
    assert type(store).__name__ == "MemorySessionScopeStore"
    store2 = create_session_scope_store("  ")
    assert type(store2).__name__ == "MemorySessionScopeStore"
