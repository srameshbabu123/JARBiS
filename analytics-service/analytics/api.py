"""Backend-only HTTP API. No UI, static assets, or database access to OLTP."""
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
import secrets
import bcrypt
from fastapi import FastAPI, Request, Response, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.encoders import jsonable_encoder
from pydantic import ValidationError
from psycopg import Error as DatabaseError
from psycopg_pool import PoolTimeout
from starlette.concurrency import run_in_threadpool
from .config import Settings
from .database import make_pool, migrate
from .models import ReportFilter
from .reports import Reports
from .sessions import Sessions, COOKIE


def create_app(settings=None, pool=None, sessions=None, reports=None):
    settings = settings or Settings.load()
    owned = pool is None and sessions is None
    pool = make_pool(settings) if owned else pool
    sessions = sessions or Sessions(pool)
    reports = reports or Reports(pool)

    @asynccontextmanager
    async def lifespan(app):
        if owned:
            pool.open()
            try:
                await run_in_threadpool(pool.wait, 10)
                await run_in_threadpool(migrate, pool)
                yield
            finally:
                pool.close()
        else:
            yield

    app = FastAPI(title="Internal analytics API", version="1.0.0", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    @app.exception_handler(RequestValidationError)
    @app.exception_handler(ValidationError)
    async def invalid(request, exc):
        return JSONResponse({"detail": "Invalid filters or request parameters"}, status_code=400)

    @app.exception_handler(DatabaseError)
    @app.exception_handler(PoolTimeout)
    async def database_unavailable(request, exc):
        return JSONResponse({"detail": "Analytics temporarily unavailable"}, status_code=503)

    @app.middleware("http")
    async def security(request, call_next):
        origin = request.headers.get("origin")
        own_origin = str(request.base_url).rstrip("/")
        if origin and origin != own_origin and origin not in settings.origins:
            return JSONResponse({"detail": "Origin not allowed"}, status_code=403)
        try:
            request.state.session = await run_in_threadpool(sessions.find, request.cookies.get(COOKIE))
        except (DatabaseError, PoolTimeout):
            return JSONResponse({"detail": "Analytics temporarily unavailable"}, status_code=503)
        public = request.url.path in {"/login", "/session/csrf", "/actuator/health"}
        session = request.state.session
        if not public and (not session or session["username"] != settings.username):
            response = JSONResponse({"detail": "Authentication required"}, status_code=401)
        else:
            response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Content-Security-Policy"] = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"
        return response

    app.add_middleware(CORSMiddleware, allow_origins=list(settings.origins), allow_credentials=True,
                       allow_methods=["GET", "POST", "OPTIONS"], allow_headers=["Content-Type", "X-CSRF-TOKEN", "X-Requested-With"])

    def cookie(response, raw):
        response.set_cookie(COOKIE, raw, httponly=True, secure=settings.secure_cookie, samesite="strict", path="/")

    def csrf(request):
        session = request.state.session
        token = request.headers.get("X-CSRF-TOKEN", "")
        if not session or not secrets.compare_digest(token, session["csrf_token"]):
            raise HTTPException(403, "Invalid CSRF token")

    @app.get("/session/csrf")
    def get_csrf(request: Request, response: Response):
        session = request.state.session
        if not session:
            raw, session = sessions.create()
            cookie(response, raw)
        return {"parameterName": "_csrf", "headerName": "X-CSRF-TOKEN", "token": session["csrf_token"]}

    @app.post("/login", status_code=204)
    async def login(request: Request):
        csrf(request)
        form = await request.form(max_fields=4, max_files=0)
        username, password = str(form.get("username", "")), str(form.get("password", ""))
        encoded = password.encode()
        if len(encoded) > 72 or len(username) > 100:
            raise HTTPException(401, "Invalid credentials")
        valid = await run_in_threadpool(bcrypt.checkpw, encoded, settings.password_hash.encode())
        if not valid or not secrets.compare_digest(username, settings.username):
            raise HTTPException(401, "Invalid credentials")
        await run_in_threadpool(sessions.revoke, request.cookies.get(COOKIE))
        raw, _ = await run_in_threadpool(sessions.create, settings.username)
        response = Response(status_code=204)
        cookie(response, raw)
        return response

    @app.get("/session/me")
    def me():
        return {"username": settings.username, "roles": ["ROLE_ANALYST"]}

    @app.post("/logout", status_code=204)
    def logout(request: Request):
        csrf(request)
        sessions.revoke(request.cookies.get(COOKIE))
        response = Response(status_code=204)
        response.delete_cookie(COOKIE, path="/", secure=settings.secure_cookie, httponly=True, samesite="strict")
        return response

    @app.get("/actuator/health")
    def health():
        if pool is not None:
            with pool.connection() as conn:
                conn.execute("SELECT 1")
        return {"status": "UP"}

    def parse_filter(request, require_dates=False):
        q = request.query_params
        end = q.get("to") or datetime.now(timezone.utc).date().isoformat()
        if require_dates and (not q.get("from") or not q.get("to")):
            raise HTTPException(400, "from and to are required")
        try:
            start = q.get("from") or (date.fromisoformat(end) - timedelta(days=29)).isoformat()
            return ReportFilter(**{"from": start, "to": end, "instrumentId": q.get("instrumentId"),
                                  "segment": q.get("segment"), "currency": q.get("currency", "USD"), "dataset": q.get("dataset", "LIVE")})
        except (ValueError, ValidationError):
            raise HTTPException(400, "Invalid reporting filters")

    @app.get("/admin/analytics/options")
    def options():
        return JSONResponse(jsonable_encoder(reports.options()))

    @app.get("/admin/analytics/dashboard")
    def dashboard(request: Request):
        return JSONResponse(jsonable_encoder(reports.dashboard(parse_filter(request))))

    @app.get("/admin/analytics/trades")
    def trades(request: Request):
        try:
            page = int(request.query_params.get("page", "0"))
            if not 0 <= page <= 10000:
                raise ValueError()
        except ValueError:
            raise HTTPException(400, "Invalid page")
        return JSONResponse(jsonable_encoder(reports.trades(parse_filter(request, True), page)))

    return app
