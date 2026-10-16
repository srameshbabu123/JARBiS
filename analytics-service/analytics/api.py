"""Backend-only HTTP API. No UI, static assets, or database access to OLTP."""
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta, timezone
from uuid import UUID
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
from .operations import Operations
from .models import DisputeCreate, DisputeChange, DisputeNote


def create_app(settings=None, pool=None, sessions=None, reports=None, operations=None):
    settings = settings or Settings.load()
    owned = pool is None and sessions is None
    pool = make_pool(settings) if owned else pool
    sessions = sessions or Sessions(pool)
    reports = reports or Reports(pool)
    operations = operations or Operations(pool)

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
        accounts = settings.accounts()
        user = session.get("username") if session else None
        role = session.get("role", "ANALYST") if session else None
        authenticated = user in accounts and accounts[user][1] == role
        operational = request.url.path.startswith(("/admin/investigations", "/admin/disputes")) or request.url.path == "/admin/analytics/trades"
        if not public and not authenticated:
            response = JSONResponse({"detail": "Authentication required"}, status_code=401)
        elif operational and role != "OPERATIONS":
            response = JSONResponse({"detail": "Operations role required"}, status_code=403)
        elif request.url.path.startswith("/admin/") and request.method in {"POST", "PATCH", "PUT", "DELETE"} and (not session or not secrets.compare_digest(request.headers.get("X-CSRF-TOKEN", ""), session["csrf_token"])):
            response = JSONResponse({"detail": "Invalid CSRF token"}, status_code=403)
        else:
            response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Content-Security-Policy"] = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"
        return response

    app.add_middleware(CORSMiddleware, allow_origins=list(settings.origins), allow_credentials=True,
                       allow_methods=["GET", "POST", "PATCH", "OPTIONS"], allow_headers=["Content-Type", "X-CSRF-TOKEN", "X-Requested-With"])

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
        account = settings.accounts().get(username)
        password_hash = account[0] if account else settings.password_hash
        valid = await run_in_threadpool(bcrypt.checkpw, encoded, password_hash.encode())
        if not valid or account is None:
            raise HTTPException(401, "Invalid credentials")
        await run_in_threadpool(sessions.revoke, request.cookies.get(COOKIE))
        raw, _ = await run_in_threadpool(sessions.create, username, account[1])
        response = Response(status_code=204)
        cookie(response, raw)
        return response

    @app.get("/session/me")
    def me(request: Request):
        return {"username": request.state.session["username"], "roles": ["ROLE_" + request.state.session.get("role", "ANALYST")]}

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
                                  "segment": q.get("segment"), "portfolioSize": q.get("portfolioSize"), "activityLevel": q.get("activityLevel"), "currency": q.get("currency", "USD"), "dataset": q.get("dataset", "ALL")})
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
        extra = {}
        for key, field in (("clientId", "client_id"), ("orderId", "order_id"), ("tradeId", "trade_id")):
            if key in request.query_params:
                try:
                    value = int(request.query_params[key])
                    if value <= 0: raise ValueError()
                    extra[field] = value
                except ValueError:
                    raise HTTPException(400, "Use positive numeric client, order and trade IDs")
        return JSONResponse(jsonable_encoder(reports.trades(parse_filter(request, True), page, **extra)))

    def actor(request):
        return request.state.session["username"]

    def page_number(request):
        try:
            page = int(request.query_params.get("page", "0"))
            if not 0 <= page <= 10000: raise ValueError()
            return page
        except ValueError:
            raise HTTPException(400, "Invalid page")

    def positive_id(request, key):
        value = request.query_params.get(key)
        if value is None: return None
        try:
            result = int(value)
            if result <= 0: raise ValueError()
            return result
        except ValueError:
            raise HTTPException(400, "Invalid identifier")

    def assignee_allowed(value):
        if value is not None and (value not in settings.accounts() or settings.accounts()[value][1] != "OPERATIONS"):
            raise HTTPException(400, "Assignee must be a provisioned operations user")

    @app.get("/admin/investigations/orders")
    def order_search(request: Request):
        status = request.query_params.get("status")
        if status is not None and status not in {"PENDING", "PARTIALLY_FILLED", "COMPLETED", "CANCELLED"}:
            raise HTTPException(400, "Invalid order status")
        return JSONResponse(jsonable_encoder(operations.search(parse_filter(request), actor(request), page_number(request), positive_id(request,"clientId"), positive_id(request,"orderId"), status)))

    @app.get("/admin/investigations/orders/{order_id}/timeline")
    def timeline(order_id: int, request: Request):
        if order_id <= 0: raise HTTPException(400, "Invalid order ID")
        return JSONResponse(jsonable_encoder(operations.timeline(order_id, actor(request))))

    @app.get("/admin/investigations/orders/{order_id}/export")
    def timeline_export(order_id: int, request: Request):
        if order_id <= 0: raise HTTPException(400, "Invalid order ID")
        return Response(operations.export(order_id, actor(request)), media_type="text/csv", headers={"Content-Disposition": f'attachment; filename="order-{order_id}-evidence.csv"'})

    @app.get("/admin/investigations/trades/{trade_id}")
    def trade_detail(trade_id: int, request: Request):
        if trade_id <= 0: raise HTTPException(400, "Invalid trade ID")
        return JSONResponse(jsonable_encoder(operations.trade(trade_id, actor(request))))

    @app.get("/admin/disputes/insights")
    def case_insights(request: Request):
        # Case insights filter by opened date only; reject unrelated trading filters rather than ignore them.
        if any(k in request.query_params for k in ("instrumentId", "segment", "portfolioSize", "activityLevel", "currency", "dataset")):
            raise HTTPException(400, "Dispute insights accept from/to dates only")
        return JSONResponse(jsonable_encoder(operations.insights(parse_filter(request), actor(request))))

    @app.get("/admin/disputes")
    def cases(request: Request):
        status = request.query_params.get("status")
        if status is not None and status not in {"OPEN", "IN_REVIEW", "RESOLVED", "CLOSED"}:
            raise HTTPException(400, "Invalid dispute status")
        return JSONResponse(jsonable_encoder(operations.cases(actor(request), page_number(request), status, positive_id(request,"clientId"), positive_id(request,"orderId"))))

    @app.post("/admin/disputes", status_code=201)
    def create_case(body: DisputeCreate, request: Request):
        assignee_allowed(body.assignedTo)
        return JSONResponse(jsonable_encoder(operations.create(body, actor(request))), status_code=201)

    @app.get("/admin/disputes/{case_id}")
    def case_detail(case_id: UUID, request: Request):
        return JSONResponse(jsonable_encoder(operations.detail(case_id, actor(request))))

    @app.get("/admin/disputes/{case_id}/evidence")
    def case_evidence(case_id: UUID, request: Request):
        return JSONResponse(jsonable_encoder(operations.detail(case_id, actor(request))["evidence"]))

    @app.patch("/admin/disputes/{case_id}")
    def change_case(case_id: UUID, body: DisputeChange, request: Request):
        assignee_allowed(body.assignedTo)
        return JSONResponse(jsonable_encoder(operations.change(case_id, body, actor(request))))

    @app.post("/admin/disputes/{case_id}/notes", status_code=201)
    def case_note(case_id: UUID, body: DisputeNote, request: Request):
        return JSONResponse(jsonable_encoder(operations.note(case_id, body.text, actor(request))), status_code=201)

    return app
