"""Bounded OLAP-only pool and additive, checksummed SQL migrations."""
import hashlib
from pathlib import Path
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool


def make_pool(settings):
    return ConnectionPool(settings.db_url, min_size=1, max_size=5, timeout=5, open=False,
                          kwargs={"user": settings.db_user, "password": settings.db_password,
                                  "row_factory": dict_row, "connect_timeout": 5,
                                  "options": "-c statement_timeout=10000 -c timezone=UTC"})


def migrate(pool):
    with pool.connection() as conn:
        # API and consumer may start together. The lock covers migration bookkeeping and DDL.
        conn.execute("SELECT pg_advisory_xact_lock(7646015)")
        conn.execute("CREATE SCHEMA IF NOT EXISTS reporting_migrations")
        conn.execute("CREATE TABLE IF NOT EXISTS reporting_migrations.python_history (version TEXT PRIMARY KEY, checksum TEXT NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())")
        for path in sorted((Path(__file__).resolve().parents[1] / "migrations").glob("V*.sql")):
            source = path.read_text()
            checksum = hashlib.sha256(source.encode()).hexdigest()
            previous = conn.execute("SELECT checksum FROM reporting_migrations.python_history WHERE version=%s", (path.name,)).fetchone()
            if previous:
                if previous["checksum"] != checksum:
                    raise RuntimeError(f"Applied migration changed: {path.name}")
                continue
            # V1/V2 are the original idempotent SQL: existing Flyway databases can be adopted without erasing data.
            conn.execute(source, prepare=False)
            conn.execute("INSERT INTO reporting_migrations.python_history(version,checksum) VALUES (%s,%s)", (path.name, checksum))
