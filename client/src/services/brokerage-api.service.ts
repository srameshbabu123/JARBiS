import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { tap, catchError, of, forkJoin, map, Observable } from "rxjs";
import { SessionService } from "./session.service";
import {
  User,
  AuthResponse,
  Account,
  Holding,
  Order,
  CreateOrder,
  MarketDataEvent,
} from "../models/brokerage.models";
import { INSTRUMENTS_DIRECTORY } from "../data/sample-market";

const FAUXNANCE_API_KEY = "fnx_dev_ynMH4HwnxqC9EljRB9BjjtbRyPLUgPOP";
const FAUXNANCE_BASE_URL =
  "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1";

export const KNOWN_TICKER_ASSET_MAP: Record<string, number> = {
  AAPL: 58,
  MSFT: 59,
  NVDA: 60,
  GOOGL: 61,
  TSLA: 62,
  AMZN: 63,
  META: 64,
  NFLX: 65,
  AMD: 66,
  PLTR: 67,
  COIN: 68,
  DIS: 69,
  INTC: 70,
  SPY: 71,
  QQQ: 72,
  BTC: 73,
  ETH: 74,
  JPM: 75,
  V: 76,
  WMT: 77,
  BABA: 78,
  CRM: 79,
  ORCL: 80,
  CSCO: 81,
  AVGO: 82,
  ADBE: 83,
};

for (const item of INSTRUMENTS_DIRECTORY) {
  if (item.symbol && item.id) {
    KNOWN_TICKER_ASSET_MAP[item.symbol.toUpperCase()] = item.id;
  }
}

interface CachedQuote {
  event: MarketDataEvent;
  timestamp: number;
}

@Injectable({ providedIn: "root" })
export class BrokerageApiService {
  private http = inject(HttpClient);
  private session = inject(SessionService);
  private quoteCache = new Map<string, CachedQuote>();
  private readonly CACHE_TTL_MS = 60000; // 60s client cache to conserve Fauxnance API tokens

  login(body: { email: string; password: string }) {
    return this.http
      .post<AuthResponse>("/api/auth/login", body)
      .pipe(tap((r) => this.session.remember(r)));
  }

  createUser(body: { fullName: string; email: string; password: string }) {
    return this.http
      .post<AuthResponse>("/api/auth/register", body)
      .pipe(tap((r) => this.session.remember(r)));
  }

  user(id: number) {
    return this.http.get<User>(`/api/users/${id}`);
  }

  balance(id: number) {
    return this.http.get<number>(`/api/users/${id}/balance`);
  }

  account(id: number) {
    return this.http.get<Account>(`/api/accounts/${id}`);
  }

  createAccount(body: {
    userId: number;
    accountType: string;
    currency: string;
    initialBalance?: number;
  }) {
    return this.http.post<Account>("/api/accounts", body);
  }

  myAccounts() {
    return this.http.get<Account[]>("/api/accounts/mine");
  }

  changeCash(id: number, amount: number, action: "deposit" | "withdraw") {
    return this.http.post<Account>(`/api/accounts/${id}/${action}`, { amount });
  }

  holdings(id: number) {
    return this.http.get<Holding[]>(`/api/holdings/account/${id}`);
  }

  orders(id: number) {
    return this.http.get<Order[]>(`/api/orders/account/${id}`);
  }

  updateUser(id: number, body: { fullName: string; email: string }) {
    return this.http.put<User>(`/api/users/${id}`, body);
  }

  createOrder(body: CreateOrder) {
    return this.http.post<Order>("/api/orders", body);
  }

  latestMarketData(symbol: string): Observable<MarketDataEvent> {
    const sym = (symbol || "").trim().toUpperCase();
    if (!sym) {
      return of({
        symbol: "",
        price: 0,
        change: 0,
        changePercent: 0,
      } as MarketDataEvent);
    }

    const cached = this.quoteCache.get(sym);
    if (cached && Date.now() - cached.timestamp < this.CACHE_TTL_MS) {
      return of(cached.event);
    }

    return this.http
      .get<MarketDataEvent>(`/api/market-data/${sym}/latest`)
      .pipe(
        tap((event) => {
          if (event && typeof event.price === "number") {
            this.quoteCache.set(sym, { event, timestamp: Date.now() });
          }
        }),
        catchError(() => this.fetchDirectFauxnanceQuote(sym)),
      );
  }

  fetchDirectFauxnanceQuote(symbol: string): Observable<MarketDataEvent> {
    const sym = (symbol || "").trim().toUpperCase();
    const headers = new HttpHeaders({ "X-Api-Key": FAUXNANCE_API_KEY });
    return this.http
      .get<any>(`${FAUXNANCE_BASE_URL}/quotes?symbols=${sym}`, { headers })
      .pipe(
        map((res) => {
          const quotes = res?.data?.quotes;
          if (Array.isArray(quotes) && quotes.length > 0) {
            const item = quotes.find((q: any) => q.symbol === sym) || quotes[0];
            const q = item.quote;
            if (q && typeof q.price === "number") {
              const event: MarketDataEvent = {
                symbol: q.symbol || sym,
                price: q.price,
                bid: q.bid,
                ask: q.ask,
                spreadBps: q.spreadBps,
                currency: q.currency || "USD",
                change: q.change,
                changePercent: q.changePercent,
                previousClose: q.previousClose,
                source: item.source || "fauxnance",
                stale: Boolean(item.stale),
                marketState: q.marketState,
                asOf: q.asOf,
                capturedAt: new Date().toISOString(),
              };
              this.quoteCache.set(sym, { event, timestamp: Date.now() });
              return event;
            }
          }
          throw new Error(`No quote found for ${sym}`);
        }),
      );
  }

  fetchBatchFauxnanceQuotes(symbols: string[]): Observable<MarketDataEvent[]> {
    if (!symbols.length) return of([]);
    const cleanSymbols = symbols
      .map((s) => s.trim().toUpperCase())
      .filter((s) => Boolean(s));
    if (!cleanSymbols.length) return of([]);

    const now = Date.now();
    const missingOrStale: string[] = [];
    const cachedResults: MarketDataEvent[] = [];

    for (const sym of cleanSymbols) {
      const cached = this.quoteCache.get(sym);
      if (cached && now - cached.timestamp < this.CACHE_TTL_MS) {
        cachedResults.push(cached.event);
      } else {
        missingOrStale.push(sym);
      }
    }

    if (missingOrStale.length === 0) {
      return of(cachedResults);
    }

    const headers = new HttpHeaders({ "X-Api-Key": FAUXNANCE_API_KEY });
    const chunks: string[][] = [];
    for (let i = 0; i < missingOrStale.length; i += 25) {
      chunks.push(missingOrStale.slice(i, i + 25));
    }

    const chunkRequests = chunks.map((chunk) => {
      const symString = chunk.join(",");
      return this.http
        .get<any>(`${FAUXNANCE_BASE_URL}/quotes?symbols=${symString}`, {
          headers,
        })
        .pipe(
          map((res) => {
            const quotes = res?.data?.quotes;
            if (!Array.isArray(quotes)) return [];
            return quotes
              .filter((item: any) => item?.quote?.price != null)
              .map((item: any) => {
                const q = item.quote;
                const evt: MarketDataEvent = {
                  symbol: q.symbol || item.symbol,
                  price: q.price,
                  bid: q.bid,
                  ask: q.ask,
                  spreadBps: q.spreadBps,
                  currency: q.currency || "USD",
                  change: q.change,
                  changePercent: q.changePercent,
                  previousClose: q.previousClose,
                  source: item.source || "fauxnance",
                  stale: Boolean(item.stale),
                  marketState: q.marketState,
                  asOf: q.asOf,
                  capturedAt: new Date().toISOString(),
                };
                this.quoteCache.set(evt.symbol.toUpperCase(), {
                  event: evt,
                  timestamp: Date.now(),
                });
                return evt;
              });
          }),
          catchError(() => of([])),
        );
    });

    return forkJoin(chunkRequests).pipe(
      map((results) => {
        const fetchedEvents = results.flat();
        return [...cachedResults, ...fetchedEvents];
      }),
    );
  }

  resolveAsset(symbol: string): Observable<{ id: number; name: string }> {
    const sym = (symbol || "").trim().toUpperCase();
    if (!sym) throw new Error("An asset symbol is required.");

    return this.http.get<{ id: number; name: string }>(
      `/api/assets/by-symbol/${encodeURIComponent(sym)}`,
    );
  }

  marketDataHistory(symbol: string, from: string, to: string) {
    return this.http.get<MarketDataEvent[]>(
      `/api/market-data/${symbol}/history`,
      {
        params: { from, to },
      },
    );
  }
}
