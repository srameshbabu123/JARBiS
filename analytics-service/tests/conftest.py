from datetime import datetime, timezone
from decimal import Decimal
from uuid import uuid4
import pytest


@pytest.fixture
def event_data():
    return {"schemaVersion": 1, "eventId": str(uuid4()), "type": "TradeSettled", "occurredAt": "2026-03-01T00:00:00Z",
            "transactionId": 1, "assetId": 8, "instrument": "AAPL", "assetType": "STOCK", "quantity": "2", "executionPrice": "10", "currency": "USD",
            "buyer": {"orderId": 2, "accountId": 10, "userId": 1, "accountType": "INVESTMENT", "currency": "USD"},
            "seller": {"orderId": 3, "accountId": 20, "userId": 2, "accountType": "CHECKING", "currency": "USD"}}
