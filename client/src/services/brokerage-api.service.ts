import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { tap, catchError, of, forkJoin, map, Observable } from "rxjs";
import { SessionService } from "./session.service";
import {
  User,
  AuthResponse,
  Account,
  Holding,
  Order,
  CreateOrder,
  CreateOrderDispute,
  OrderDispute,
  MarketDataEvent,
} from "../models/brokerage.models";
import { MarketSymbolMetadata } from "../models/market.models";

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
    return this.http.get<Account>(`/api/accounts/mine/${id}`);
  }

  createAccount(body: {
    userId: number;
    accountType: string;
    currency: string;
    initialBalance?: number;
  }) {
    return this.http.post<Account>("/api/accounts", body);
  }

  initializeMyAccount() {
    return this.http.post<Account>("/api/accounts/mine/initialize", {});
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
      .get<MarketDataEvent>(
        `/api/market-data/${encodeURIComponent(sym)}/latest`,
      )
      .pipe(
        tap((event) => {
          if (event && typeof event.price === "number") {
            this.quoteCache.set(sym, { event, timestamp: Date.now() });
          }
        }),
      );
  }

  assets() {
    return this.http.get<{ id: number; name: string; type: string }[]>(
      "/api/assets",
    );
  }

  latestQuotes(symbols: string[]): Observable<MarketDataEvent[]> {
    const unique = [
      ...new Set(symbols.map((s) => s.trim().toUpperCase()).filter(Boolean)),
    ];
    if (!unique.length) return of([]);
    return forkJoin(
      unique.map((symbol) =>
        this.latestMarketData(symbol).pipe(
          // A symbol without collected data must not hide the other quotes.
          catchError(() => of(null)),
        ),
      ),
    ).pipe(
      map((events) => events.filter((e): e is MarketDataEvent => e !== null)),
    );
  }

  resolveAsset(symbol: string): Observable<{ id: number; name: string }> {
    const sym = (symbol || "").trim().toUpperCase();
    if (!sym) throw new Error("An asset symbol is required.");

    return this.http.get<{ id: number; name: string }>(
      `/api/assets/by-symbol/${encodeURIComponent(sym)}`,
    );
  }

  marketDataHistory(symbol: string, range: string, to: string) {
    return this.http.get<MarketDataEvent[]>(
      `/api/market-data/${encodeURIComponent(symbol)}/history`,
      {
        params: { range, to },
      },
    );
  }

  marketMetadata(symbols: string[]) {
    const normalized = [
      ...new Set(symbols.map((symbol) => symbol.trim().toUpperCase()).filter(Boolean)),
    ];
    if (!normalized.length) return of([] as MarketSymbolMetadata[]);

    return this.http.get<MarketSymbolMetadata[]>("/api/market-data/metadata", {
      params: { symbols: normalized.join(",") },
    });
  }

  submitOrderDispute(orderId: number, body: CreateOrderDispute) {
    return this.http.post<OrderDispute>(`/api/orders/${orderId}/disputes`, body);
  }

  orderDisputes(orderId: number) {
    return this.http.get<OrderDispute[]>(`/api/orders/${orderId}/disputes`);
  }
}
