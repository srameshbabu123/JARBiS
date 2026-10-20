import {
  Injectable,
  inject,
  signal,
  effect,
  untracked,
  DestroyRef,
} from "@angular/core";
import { Router } from "@angular/router";
import { MarketStock } from "../data/sample-market";
import { BrokerageApiService } from "./brokerage-api.service";
import { SessionService } from "./session.service";
import { MarketDataEvent } from "../models/brokerage.models";

@Injectable({ providedIn: "root" })
export class MarketService {
  private readonly router = inject(Router);
  private readonly api = inject(BrokerageApiService);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);
  private searchDebounceTimer: any = null;

  readonly availableSectors = [
    "All sectors",
    "Technology",
    "Automotive",
    "Consumer",
    "Financials",
    "Aerospace",
    "Healthcare",
    "Energy",
    "Industrial",
    "Cryptocurrency",
  ];

  private readonly stocksState = signal<MarketStock[]>([]);
  get stocks(): MarketStock[] {
    return this.stocksState();
  }
  set stocks(value: MarketStock[]) {
    this.stocksState.set(value);
  }

  private readonly selectedState = signal<MarketStock>({ id: 0, symbol: "", name: "", price: 0, change: 0, sector: "Unknown", cap: 0, color: "var(--purple)" });
  get selected(): MarketStock {
    return this.selectedState();
  }
  set selected(value: MarketStock) {
    this.selectedState.set(value);
  }

  private readonly historyState = signal<MarketDataEvent[]>([]);
  get history() {
    return this.historyState();
  }

  private readonly queryState = signal<string>("");
  get query() {
    return this.queryState();
  }
  set query(value: string) {
    this.queryState.set(value);
    clearTimeout(this.searchDebounceTimer);
    const clean = (value || "").trim().toUpperCase();
    if (clean.length >= 1 && clean.length <= 6 && /^[A-Z]+$/.test(clean)) {
      if (!this.stocks.some((s) => s.symbol === clean)) {
        this.searchDebounceTimer = setTimeout(() => {
          this.api.latestMarketData(clean).subscribe({
            next: (e) => {
              if (
                e &&
                typeof e.price === "number" &&
                !this.stocks.some((s) => s.symbol === clean)
              ) {
                const newStock: MarketStock = {
                  id: 0,
                  symbol: clean,
                  name: clean,
                  price: Math.round(e.price * 100) / 100,
                  change:
                    typeof e.changePercent === "number"
                      ? Math.round(e.changePercent * 100) / 100
                      : 0,
                  sector: "Unknown",
                  cap: 0,
                  color: "var(--purple)",
                };
                this.stocks = [newStock, ...this.stocks];
              }
            },
            error: () => {},
          });
        }, 350);
      }
    }
  }

  private readonly sectorState = signal<string>("All sectors");
  get sector() {
    return this.sectorState();
  }
  set sector(value: string) {
    this.sectorState.set(value);
  }

  private readonly gainersState = signal<boolean>(false);
  get gainers() {
    return this.gainersState();
  }
  set gainers(value: boolean) {
    this.gainersState.set(value);
  }

  private readonly rangeState = signal<string>("1M");
  get range() {
    return this.rangeState();
  }
  set range(value: string) {
    this.rangeState.set(value);
    this.refreshHistory(this.selected.symbol, value);
  }

  private readonly watchedState = signal<string[]>(this.loadWatchlist());
  get watched() {
    return this.watchedState();
  }
  set watched(value: string[]) {
    this.watchedState.set(value);
  }

  constructor() {
    effect(() => {
      const token = this.session.token;
      if (token) {
        untracked(() => {
          this.refreshQuotes();
          this.refreshHistory(this.selected.symbol, this.range);
        });
      }
    });

    // Fetch live quotes immediately on startup
    this.refreshQuotes();

    // 60-second polling interval to preserve external API token quota
    const intervalId = setInterval(() => {
      if (
        typeof document !== "undefined" &&
        document.visibilityState === "hidden"
      ) {
        return; // Pause background polling when tab is hidden
      }
      this.refreshQuotes();
      if (this.session.token) {
        this.refreshHistory(this.selected.symbol, this.range);
      }
    }, 60000);

    let onVisibility: (() => void) | null = null;
    if (typeof document !== "undefined") {
      onVisibility = () => {
        if (document.visibilityState === "visible") {
          this.refreshQuotes();
        }
      };
      document.addEventListener("visibilitychange", onVisibility);
    }

    this.destroyRef.onDestroy(() => {
      clearInterval(intervalId);
      if (typeof document !== "undefined" && onVisibility) {
        document.removeEventListener("visibilitychange", onVisibility);
      }
    });
  }

  loadWatchlist() {
    try {
      const v = JSON.parse(
        localStorage.getItem("velocity-watchlist") ||
          "[]",
      );
      return Array.isArray(v) ? v.filter((x) => typeof x === "string") : [];
    } catch {
      return [];
    }
  }

  get filtered() {
    return this.stocks.filter(
      (s) =>
        (s.name + " " + s.symbol)
          .toLowerCase()
          .includes(this.query.toLowerCase()) &&
        (this.sector === "All sectors" || s.sector === this.sector) &&
        (!this.gainers || s.change > 0),
    );
  }

  toggle(s: string) {
    this.watched = this.watched.includes(s)
      ? this.watched.filter((x) => x !== s)
      : [...this.watched, s];
    try {
      localStorage.setItem("velocity-watchlist", JSON.stringify(this.watched));
    } catch {
      /* Keep the in-memory watchlist if browser storage is unavailable. */
    }
  }

  chart(s: MarketStock) {
    this.selected = s;
    this.refreshHistory(s.symbol, this.range);
    void this.router.navigateByUrl("/charts");
  }

  refreshQuotes() {
    const symbols = [...new Set([...this.stocks.map((s) => s.symbol), ...this.watched])];
    if (!symbols.length) return;
    this.api.fetchBatchFauxnanceQuotes(symbols).subscribe({
      next: (events) => {
        if (!Array.isArray(events) || !events.length) return;
        const eventMap = new Map(events.map((e) => [e.symbol, e]));
        const existing = new Set(this.stocks.map(s => s.symbol));
        const fetched = events.filter(e => !existing.has(e.symbol) && typeof e.price === "number").map(e => ({ id: 0, symbol: e.symbol, name: e.symbol, price: e.price, change: e.changePercent ?? 0, sector: "Unknown", cap: 0, color: "var(--purple)" }));
        this.stocks = [...this.stocks.map((s) => {
          const e = eventMap.get(s.symbol);
          if (e && typeof e.price === "number") {
            const changePct =
              typeof e.changePercent === "number"
                ? Math.round(e.changePercent * 100) / 100
                : s.change;
            return {
              ...s,
              price: Math.round(e.price * 100) / 100,
              change: changePct,
            };
          }
          return s;
        }),
        ...fetched];
        const selEvent = eventMap.get(this.selected.symbol);
        if (selEvent && typeof selEvent.price === "number") {
          const changePct =
            typeof selEvent.changePercent === "number"
              ? Math.round(selEvent.changePercent * 100) / 100
              : this.selected.change;
          this.selected = {
            ...this.selected,
            price: Math.round(selEvent.price * 100) / 100,
            change: changePct,
          };
        }
      },
      error: () => {},
    });
  }

  refreshHistory(symbol: string, range: string) {
    if (!symbol) return;
    const now = new Date();
    const to = now.toISOString();
    const fromDate = new Date(now);
    switch (range) {
      case "1D":
        fromDate.setHours(now.getHours() - 24);
        break;
      case "1W":
        fromDate.setDate(now.getDate() - 7);
        break;
      case "1M":
        fromDate.setDate(now.getDate() - 30);
        break;
      case "3M":
        fromDate.setDate(now.getDate() - 90);
        break;
      case "1Y":
      case "ALL":
      default:
        fromDate.setDate(now.getDate() - 365);
        break;
    }
    const from = fromDate.toISOString();
    this.api.marketDataHistory(symbol, from, to).subscribe({
      next: (events) => {
        if (Array.isArray(events) && events.length >= 2) {
          this.historyState.set(events);
        } else {
          this.historyState.set([]);
        }
      },
      error: () => {
        this.historyState.set([]);
      },
    });
  }

  get path() {
    const events = this.historyState();
    if (events.length >= 2) {
      const prices = events.map((e) => e.price);
      const min = Math.min(...prices);
      const max = Math.max(...prices);
      const diff = max - min || 1;
      return events
        .map((e, i) => {
          const x = (i / (events.length - 1)) * 790;
          const y = 220 - ((e.price - min) / diff) * 160;
          return `${i === 0 ? "M" : "L"} ${x.toFixed(1)} ${y.toFixed(1)}`;
        })
        .join(" ");
    }
    return "";
  }
}
