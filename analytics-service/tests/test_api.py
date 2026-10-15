import secrets
import bcrypt
import pytest
from fastapi.testclient import TestClient
from analytics.api import create_app
from analytics.config import Settings
from analytics.sessions import COOKIE


class FakeSessions:
    def __init__(self):
        self.rows = {}
    def find(self, raw):
        return self.rows.get(raw)
    def create(self, username=None):
        raw = secrets.token_urlsafe(32)
        session = {"username": username, "csrf_token": secrets.token_urlsafe(32)}
        self.rows[raw] = session
        return raw, session
    def revoke(self, raw):
        self.rows.pop(raw, None)


class FakeReports:
    def options(self):
        return {"instruments": [], "segments": [], "currencies": [], "datasets": []}
    def dashboard(self, f):
        return {"filter": f.model_dump(by_alias=True), "summary": {"trades": 0}}
    def trades(self, f, page):
        return {"rows": [], "page": page, "pageSize": 25, "total": 0}


@pytest.fixture
def client():
    settings = Settings("postgresql://localhost/test", "test", "test", "analyst", bcrypt.hashpw(b"test-only", bcrypt.gensalt(rounds=4)).decode(), ("http://localhost:4200",))
    with TestClient(create_app(settings, sessions=FakeSessions(), reports=FakeReports())) as client:
        yield client


def login(client):
    token = client.get("/session/csrf").json()["token"]
    return client.post("/login", data={"username": "analyst", "password": "test-only"}, headers={"X-CSRF-TOKEN": token})


def test_anonymous_and_client_token_are_not_staff(client):
    assert client.get("/admin/analytics/options").status_code == 401
    assert client.get("/admin/analytics/options", headers={"Authorization": "Bearer client-jwt"}).status_code == 401
    assert client.get("/session/me").status_code == 401
    assert client.get("/login").status_code == 405


def test_login_rotates_session_and_csrf_and_logout_revokes(client):
    before = client.get("/session/csrf").json()["token"]
    cookie = client.cookies.get(COOKIE)
    response = login(client)
    assert response.status_code == 204
    assert "location" not in response.headers
    assert client.cookies.get(COOKIE) != cookie
    assert "HttpOnly" in response.headers["set-cookie"]
    assert client.get("/session/me").json() == {"username": "analyst", "roles": ["ROLE_ANALYST"]}
    assert client.post("/logout", headers={"X-CSRF-TOKEN": before}).status_code == 403
    after = client.get("/session/csrf").json()["token"]
    assert client.post("/logout", headers={"X-CSRF-TOKEN": after}).status_code == 204
    assert client.get("/admin/analytics/options").status_code == 401


def test_invalid_login_and_missing_csrf(client):
    assert client.post("/login", data={"username": "analyst", "password": "test-only"}).status_code == 403
    token = client.get("/session/csrf").json()["token"]
    assert client.post("/login", data={"username": "analyst", "password": "wrong"}, headers={"X-CSRF-TOKEN": token}).status_code == 401


def test_cors_allowlist(client):
    response = client.options("/admin/analytics/options", headers={"Origin": "http://localhost:4200", "Access-Control-Request-Method": "GET"})
    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:4200"
    assert response.headers["access-control-allow-credentials"] == "true"
    assert client.get("/session/csrf", headers={"Origin": "https://untrusted.example"}).status_code == 403


def test_reporting_filters_and_no_frontend(client):
    assert login(client).status_code == 204
    assert client.get("/admin/analytics/options").status_code == 200
    assert client.get("/admin/analytics/dashboard?from=2026-03-30&to=2026-03-01").status_code == 400
    assert client.get("/admin/analytics/trades?page=-1&from=2026-03-01&to=2026-03-02").status_code == 400
    assert client.get("/admin/analytics/trades").status_code == 400
    response = client.get("/admin/analytics/dashboard?from=2026-03-01&to=2026-03-02")
    assert response.status_code == 200
    assert response.json()["filter"]["from"] == "2026-03-01"
    assert response.headers["cache-control"] == "no-store"
    assert client.get("/").status_code == 404
