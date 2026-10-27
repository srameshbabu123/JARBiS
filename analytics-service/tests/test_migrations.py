"""Checksum compatibility must be limited to the two published V6 variants."""
import hashlib
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from analytics import database


V6 = "V6__client_portfolio_snapshot_key_update.sql"
V7 = "V7__client_portfolio_snapshot_dataset_key.sql"
CANONICAL = "3e9bd53ced53dcddfd03dcbe1b44cd5a2a083d2425c1c285ffc433b15e450f7f"
HISTORICAL = "6365379c8a92136f5b311421a05f564e72ccdb6bfb20e8374405d91f2360cdad"
MIGRATIONS = Path(__file__).resolve().parents[1] / "migrations"


def migration_pool(monkeypatch, tmp_path, history, modified_v6=False):
    migrations = tmp_path / "migrations"
    migrations.mkdir()
    for name in (V6, V7):
        source = (MIGRATIONS / name).read_text()
        if name == V6 and modified_v6:
            source += "\n-- unexpected edit\n"
        (migrations / name).write_text(source)
    monkeypatch.setattr(database, "__file__", str(tmp_path / "analytics" / "database.py"))
    pool = MagicMock()
    conn = pool.connection.return_value.__enter__.return_value

    def execute(sql, params=None, **kwargs):
        result = MagicMock()
        if sql.startswith("SELECT checksum"):
            checksum = history.get(params[0])
            result.fetchone.return_value = {"checksum": checksum} if checksum else None
        elif sql.startswith("INSERT INTO reporting_migrations.python_history"):
            history[params[0]] = params[1]
        return result

    conn.execute.side_effect = execute
    return pool, conn


@pytest.mark.parametrize("recorded", [CANONICAL, HISTORICAL])
def test_known_v6_history_is_preserved_and_v7_runs(monkeypatch, tmp_path, recorded):
    history = {V6: recorded}
    pool, conn = migration_pool(monkeypatch, tmp_path, history)
    database.migrate(pool)
    database.migrate(pool)
    assert history[V6] == recorded
    assert history[V7] == hashlib.sha256((MIGRATIONS / V7).read_text().encode()).hexdigest()
    statements = [call.args[0] for call in conn.execute.call_args_list]
    # V6 and V7 currently share SQL; exactly one execution proves V6 was skipped
    # and V7 ran once, including across a second startup.
    assert statements.count((MIGRATIONS / V7).read_text()) == 1
    assert not any(sql.startswith(("UPDATE", "DELETE")) for sql in statements)


@pytest.mark.parametrize("recorded,modified", [("unknown", False), (HISTORICAL, True), (CANONICAL, True)])
def test_unknown_or_modified_v6_is_rejected(monkeypatch, tmp_path, recorded, modified):
    history = {V6: recorded}
    pool, _ = migration_pool(monkeypatch, tmp_path, history, modified)
    with pytest.raises(RuntimeError, match=f"Applied migration changed: {V6}"):
        database.migrate(pool)
    assert V7 not in history


def test_v6_exception_does_not_apply_to_v7(monkeypatch, tmp_path):
    pool, _ = migration_pool(monkeypatch, tmp_path, {V6: CANONICAL, V7: HISTORICAL})
    with pytest.raises(RuntimeError, match=f"Applied migration changed: {V7}"):
        database.migrate(pool)


def test_fresh_database_records_canonical_v6_and_v7(monkeypatch, tmp_path):
    history = {}
    pool, _ = migration_pool(monkeypatch, tmp_path, history)
    database.migrate(pool)
    assert history[V6] == CANONICAL
    assert V7 in history