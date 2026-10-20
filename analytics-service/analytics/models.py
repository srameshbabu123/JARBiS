from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from typing import Literal
from uuid import UUID
from pydantic import BaseModel, Field, AwareDatetime, model_validator, ConfigDict


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
    portfolioSize: Literal["UNDER_10K", "BETWEEN_10K_100K", "OVER_100K"] | None = None
    activityLevel: Literal["ACTIVE", "OCCASIONAL", "DORMANT"] | None = None
    currency: str = Field(default="USD", pattern=r"^[A-Z]{3,10}$")
    dataset: Literal["ALL", "LIVE", "LEGACY"] = "ALL"

    @model_validator(mode="after")
    def valid_range(self):
        if self.to < self.from_ or (self.to - self.from_).days > 366:
            raise ValueError("Use an inclusive date range of at most 367 days")
        return self

    def previous(self):
        days = (self.to - self.from_).days + 1
        return ReportFilter(**{**self.model_dump(by_alias=True), "from": self.from_ - timedelta(days=days), "to": self.from_ - timedelta(days=1)})

    def client_clause(self, alias="p"):
        # The alias is an internal constant, never request input. Both filters must match the SAME client.
        parts = ["TRUE"]
        if self.segment:
            parts.append(f"{alias}.account_type=%(segment)s")
        if self.portfolioSize:
            bands = {"UNDER_10K": "v.holdings_cost < 10000",
                     "BETWEEN_10K_100K": "v.holdings_cost >= 10000 AND v.holdings_cost <= 100000",
                     "OVER_100K": "v.holdings_cost > 100000"}
            parts.append(f"EXISTS (SELECT 1 FROM (SELECT holdings_cost FROM analytics.client_portfolio_snapshots WHERE user_id={alias}.user_id AND currency=%(currency)s AND (%(dataset)s='ALL' OR data_source=%(dataset)s) AND captured_at < %(end)s ORDER BY captured_at DESC, data_source DESC LIMIT 1) v WHERE {bands[self.portfolioSize]})")
        if self.activityLevel:
            last = f"(SELECT MAX(h.transaction_timestamp) FROM analytics.trade_participants hp JOIN analytics.transaction_facts h ON h.transaction_id=hp.transaction_id WHERE hp.user_id={alias}.user_id AND h.status='COMPLETED' AND (%(dataset)s='ALL' OR h.data_source=%(dataset)s) AND h.transaction_timestamp < %(end)s)"
            if self.activityLevel == "ACTIVE":
                parts.append(f"{last} >= %(month_start)s")
            elif self.activityLevel == "OCCASIONAL":
                parts.append(f"{last} >= %(activity_start)s AND {last} < %(month_start)s")
            else:
                parts.append(f"({last} IS NULL OR {last} < %(activity_start)s)")
        return " AND ".join(parts)

    def sql(self):
        clause = " t.status='COMPLETED' AND t.transaction_timestamp >= %(start)s AND t.transaction_timestamp < %(end)s AND t.currency=%(currency)s AND (%(dataset)s='ALL' OR t.data_source=%(dataset)s) "
        params = {"start": datetime.combine(self.from_, datetime.min.time(), timezone.utc),
                  "end": datetime.combine(self.to + timedelta(days=1), datetime.min.time(), timezone.utc),
                  "month_start": datetime.combine(self.to.replace(day=1), datetime.min.time(), timezone.utc),
                  "activity_start": datetime.combine(self.to - timedelta(days=89), datetime.min.time(), timezone.utc),
                  "currency": self.currency, "dataset": self.dataset, "segment": self.segment, "instrument": self.instrumentId}
        if self.instrumentId is not None:
            clause += " AND t.asset_id=%(instrument)s "
        if self.segment or self.portfolioSize or self.activityLevel:
            clause += " AND EXISTS(SELECT 1 FROM analytics.trade_participants s WHERE s.transaction_id=t.transaction_id AND " + self.client_clause("s") + ") "
        return clause, params


class LifecycleEvent(BaseModel):
    schemaVersion: Literal[1]
    eventId: UUID
    type: Literal["OrderLifecycle"]
    auditSequence: int = Field(gt=0)
    occurredAt: AwareDatetime
    orderId: int = Field(gt=0)
    clientId: int = Field(gt=0)
    accountId: int = Field(gt=0)
    assetId: int = Field(gt=0)
    eventType: Literal["ORDER_SUBMITTED", "ORDER_STATUS_CHANGED", "EXECUTION_PRICED", "FILL_RECORDED", "SETTLEMENT_FAILED", "ORDER_DELETION_REQUESTED", "DIRECT_EXECUTION_RECORDED"]
    instrument: str
    side: Literal["BUY", "SELL"]
    status: str
    quantity: Decimal = Field(gt=0, allow_inf_nan=False)
    remainingQuantity: Decimal = Field(ge=0, allow_inf_nan=False)
    orderPrice: Decimal = Field(gt=0, allow_inf_nan=False)
    cashBalance: Decimal = Field(allow_inf_nan=False)
    currency: str
    holdingQuantity: Decimal = Field(allow_inf_nan=False)
    actor: str
    details: dict


class DisputeCreate(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    requestId: UUID
    orderId: int = Field(gt=0)
    category: Literal["EXECUTION_PRICE", "UNAUTHORIZED_ORDER", "CASH_BALANCE", "HOLDINGS", "OTHER"]
    reason: str = Field(min_length=10, max_length=4000)
    assignedTo: str | None = Field(default=None, max_length=100)


class DisputeChange(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    expectedVersion: int = Field(gt=0)
    status: Literal["OPEN", "IN_REVIEW", "RESOLVED", "CLOSED"] | None = None
    assignedTo: str | None = Field(default=None, max_length=100)
    note: str = Field(default="", max_length=4000)
    resolution: str | None = Field(default=None, min_length=10, max_length=4000)


class DisputeNote(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")
    text: str = Field(min_length=1, max_length=4000)
