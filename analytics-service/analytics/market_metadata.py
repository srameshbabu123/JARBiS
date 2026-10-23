from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from threading import Lock
from typing import Iterable

import yfinance as yf


@dataclass
class CachedMetadata:
    symbol: str
    sector: str
    market_cap: int
    expires_at: datetime


class MarketMetadataProvider:
    def __init__(self, ttl_minutes: int = 240):
        self.ttl = timedelta(minutes=ttl_minutes)
        self._cache: dict[str, CachedMetadata] = {}
        self._lock = Lock()

    def get(self, symbols: Iterable[str]) -> list[dict]:
        normalized = []
        for symbol in symbols:
            s = (symbol or "").strip().upper()
            if s and s not in normalized:
                normalized.append(s)

        now = datetime.now(timezone.utc)
        results: list[dict] = []
        missing: list[str] = []

        with self._lock:
            for symbol in normalized:
                cached = self._cache.get(symbol)
                if cached and cached.expires_at > now:
                    results.append({
                        "symbol": cached.symbol,
                        "sector": cached.sector,
                        "marketCap": cached.market_cap,
                    })
                else:
                    missing.append(symbol)

        fetched = [self._fetch_symbol(symbol, now) for symbol in missing]
        with self._lock:
            for item in fetched:
                self._cache[item.symbol] = item
                results.append({
                    "symbol": item.symbol,
                    "sector": item.sector,
                    "marketCap": item.market_cap,
                })

        return sorted(results, key=lambda item: item["symbol"])

    def _fetch_symbol(self, symbol: str, now: datetime) -> CachedMetadata:
        sector = "Unknown"
        market_cap = 0
        for candidate in self._candidate_symbols(symbol):
            try:
                ticker = yf.Ticker(candidate)
                info = ticker.info or {}
                resolved_sector = self._sector_from_info(symbol, candidate, info)
                resolved_cap = self._market_cap_from_info(ticker, info)

                if resolved_sector != "Unknown":
                    sector = resolved_sector
                if resolved_cap > 0:
                    market_cap = resolved_cap
                if sector != "Unknown" and market_cap > 0:
                    break
            except Exception:
                # Metadata lookups are best-effort; fallback paths continue.
                continue

        return CachedMetadata(
            symbol=symbol,
            sector=str(sector),
            market_cap=max(0, market_cap),
            expires_at=now + self.ttl,
        )

    def _candidate_symbols(self, symbol: str) -> list[str]:
        candidates = [symbol]
        base = symbol.upper()
        if "." not in base and "-" not in base and base.isalnum():
            candidates.extend([f"{base}.NS", f"{base}.BO"])
        return candidates

    def _market_cap_from_info(self, ticker, info: dict) -> int:
        market_cap_value = info.get("marketCap")
        if market_cap_value is None:
            fast_info = getattr(ticker, "fast_info", None)
            if fast_info:
                market_cap_value = fast_info.get("market_cap")
        return int(market_cap_value or 0)

    def _sector_from_info(self, requested_symbol: str, candidate_symbol: str, info: dict) -> str:
        sector = info.get("sector") or info.get("industryDisp") or info.get("industry") or ""
        if sector:
            return str(sector)

        quote_type = str(info.get("quoteType") or "").upper()
        if quote_type in {"CRYPTOCURRENCY", "CRYPTO"}:
            return "Cryptocurrency"
        if requested_symbol.endswith("-USD") or candidate_symbol.endswith("-USD"):
            return "Cryptocurrency"

        country = str(info.get("country") or "").upper()
        if country == "INDIA" or candidate_symbol.endswith(".NS") or candidate_symbol.endswith(".BO"):
            return "India Equities"

        return "Unknown"