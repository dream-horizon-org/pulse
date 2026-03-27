"""HTTP-level checks for X-Project-ID and session DELETE behavior."""

from __future__ import annotations

import uuid

import pytest
from fastapi.testclient import TestClient

from pulse_ai.server import app
from pulse_ai.server.project_headers import PROJECT_HEADER


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_post_sessions_400_without_project_header(client: TestClient) -> None:
    r = client.post(
        f"/sessions?user_id={uuid.uuid4()}",
        headers={"Authorization": "Bearer test-token"},
    )
    assert r.status_code == 400


def test_get_sessions_list_400_without_project_header(client: TestClient) -> None:
    r = client.get(
        f"/sessions/{uuid.uuid4()}",
        headers={"Authorization": "Bearer test-token"},
    )
    assert r.status_code == 400


def test_create_list_get_delete_happy_path(client: TestClient) -> None:
    user_id = str(uuid.uuid4())
    project = "proj-a"
    headers = {
        "Authorization": "Bearer test-token",
        PROJECT_HEADER: project,
    }
    cr = client.post(f"/sessions?user_id={user_id}", headers=headers)
    assert cr.status_code == 200
    session_id = cr.json()["session_id"]

    lr = client.get(f"/sessions/{user_id}", headers=headers)
    assert lr.status_code == 200
    ids = {item["id"] for item in lr.json()}
    assert session_id in ids

    gr = client.get(f"/sessions/{user_id}/{session_id}", headers=headers)
    assert gr.status_code == 200

    dr = client.delete(f"/sessions/{user_id}/{session_id}", headers=headers)
    assert dr.status_code == 204

    dr2 = client.delete(f"/sessions/{user_id}/{session_id}", headers=headers)
    assert dr2.status_code == 204


def test_delete_404_wrong_project_when_row_exists(client: TestClient) -> None:
    user_id = str(uuid.uuid4())
    project = "proj-right"
    headers = {
        "Authorization": "Bearer test-token",
        PROJECT_HEADER: project,
    }
    cr = client.post(f"/sessions?user_id={user_id}", headers=headers)
    session_id = cr.json()["session_id"]

    bad = {
        "Authorization": "Bearer test-token",
        PROJECT_HEADER: "other-project",
    }
    r = client.delete(f"/sessions/{user_id}/{session_id}", headers=bad)
    assert r.status_code == 404
