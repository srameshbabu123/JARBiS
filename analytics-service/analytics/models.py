from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from typing import Literal
from uuid import UUID
from pydantic import BaseModel, Field, AwareDatetime, model_validator


class Participant(BaseModel):
    orderId: int = Field(gt=0)
    accountId: int = Field(gt=0)
    userId: int = Field(gt=0)
    accountType: str = Field(min_length=1, max_length=50)
    currency: str = Field(pattern=r"^[A-Z]{3,10}$")


class SettlementEvent(BaseModel):
    schemaVersion: Literal[1]
    eventId: UUID
    type: Literal["TradeSettled"]
    occurredAt: AwareDatetime
    transactionId: int = Field(gt=0)
    assetId: int = Field(gt=0)
    instrument: str = Field(min_length=1, max_length=200)
    assetType: str = Field(min_length=1, max_length=50)
    quantity: Decimal = Field(gt=0, allow_inf_nan=False)
    executionPrice: Decimal = Field(gt=0, allow_inf_nan=False)
    currency: str = Field(pattern=r"^[A-Z]{3,10}$")
    buyer: Participant
    seller: Participant


class ReportFilter(BaseModel):
    from_: date = Field(alias="from")
    to: date
    instrumentId: int | None = Field(default=None, gt=0)
    segment: str | None = Field(default=None, pattern=r"^[A-Z_]{1,50}$")
    currency: str = Field(default="USD", pattern=r"^[A-Z]{3,10}$")
    dataset: Literal["LIVE", "LEGACY"] = "LIVE"

    @model_validator(mode="after")
    def valid_range(self):
        if self.to < self.from_ or (self.to - self.from_).days > 366:
            raise ValueError("Use an inclusive date range of at most 367 days")
        return self

    def previous(self):
        days = (self.to - self.from_).days + 1
        return ReportFilter(**{**self.model_dump(by_alias=True), "from": self.from_ - timedelta(days=days), "to": self.from_ - timedelta(days=1)})

    def sql(self):
        clause = " t.status='COMPLETED' AND t.transaction_timestamp >= %(start)s AND t.transaction_timestamp < %(end)s AND t.currency=%(currency)s AND t.data_source=%(dataset)s "
        params = {"start": datetime.combine(self.from_, datetime.min.time(), timezone.utc),
                  "end": datetime.combine(self.to + timedelta(days=1), datetime.min.time(), timezone.utc),
                  "currency": self.currency, "dataset": self.dataset, "segment": self.segment, "instrument": self.instrumentId}
        if self.instrumentId is not None:
            clause += " AND t.asset_id=%(instrument)s "
        if self.segment is not None:
            clause += " AND EXISTS(SELECT 1 FROM analytics.trade_participants s WHERE s.transaction_id=t.transaction_id AND s.account_type=%(segment)s) "
        return clause, params
