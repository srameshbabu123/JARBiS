from copy import deepcopy
from datetime import date
from decimal import Decimal
from unittest.mock import Mock, patch
import pytest
from pydantic import ValidationError
from analytics.models import ReportFilter, SettlementEvent
from analytics.reports import chart_series
from analytics.consumer import process_message
from analytics.config import Settings


def test_previous_period_is_adjacent_equal_length():
    f = ReportFilter(**{"from": "2026-03-01", "to": "2026-03-30", "instrumentId": 12, "segment": "INVESTMENT"})
    assert f.previous().from_ == date(2026, 1, 30)
    assert f.previous().to == date(2026, 2, 28)
    assert f.previous().instrumentId == 12


@pytest.mark.parametrize("updates", [{"to": "2025-12-31"}, {"to": "2028-01-01"}, {"segment": "x' OR 1=1"}, {"dataset": "ALL"}, {"currency": "usd"}])
def test_invalid_filters(updates):
    with pytest.raises(ValidationError):
        ReportFilter(**{"from": "2026-01-01", "to": "2026-01-02", **updates})


def test_pandas_fills_days_without_losing_decimal_precision():
    amount = Decimal("123456789.12345678")
    rows = chart_series([{"day": date(2026, 3, 1), "trades": 2, "value": amount}], date(2026, 3, 1), date(2026, 3, 3))
    assert len(rows) == 3
    assert rows[0]["value"] == amount
    assert rows[1]["value"] == Decimal("0")
    assert rows[1]["trades"] == 0
    assert chart_series([], date(2026, 3, 1), date(2026, 3, 3)) == []


@pytest.mark.parametrize("updates", [{"schemaVersion": 2}, {"quantity": "NaN"}, {"executionPrice": "-1"}, {"occurredAt": "2026-03-01T00:00:00"}])
def test_invalid_financial_event_is_rejected(event_data, updates):
    with pytest.raises(ValidationError):
        SettlementEvent.model_validate({**event_data, **updates})


def test_offset_commits_only_after_projection_success(event_data):
    import json
    consumer, message = Mock(), Mock()
    message.value.return_value = json.dumps(event_data).encode()
    order = Mock()
    consumer.commit.side_effect = lambda **kw: order.committed()
    with patch("analytics.consumer.project", side_effect=lambda *a: order.projected()):
        process_message(consumer, message, Mock())
    assert [c[0] for c in order.mock_calls] == ["projected", "committed"]
    consumer.commit.assert_called_once_with(message=message, asynchronous=False)
    consumer.reset_mock()
    with patch("analytics.consumer.project", side_effect=RuntimeError("database failure")):
        with pytest.raises(RuntimeError):
            process_message(consumer, message, Mock())
    consumer.commit.assert_not_called()


def test_properties_and_environment_override(monkeypatch, tmp_path):
    path = tmp_path / "application.properties"
    path.write_text("ANALYTICS_DB_URL=jdbc:postgresql://localhost:8091/jarbis_olap\nANALYTICS_DB_PASSWORD=test\nANALYTICS_ADMIN_PASSWORD_HASH=$2b$12$" + "x" * 53 + "\n")
    monkeypatch.setenv("ANALYTICS_PROPERTIES_FILE", str(path))
    monkeypatch.setenv("ANALYTICS_ADMIN_USER", "staff")
    config = Settings.load()
    assert config.username == "staff"
    assert config.db_url == "postgresql://localhost:8091/jarbis_olap"
