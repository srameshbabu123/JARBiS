import {
  Injectable,
  inject,
  signal,
  effect,
  untracked,
  DestroyRef,
} from "@angular/core";
import { Router } from "@angular/router";
import { MarketStock, MarketSymbolMetadata } from "../models/market.models";
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
  private scrollAnimationFrameId: number | null = null;

  get availableSectors() {
    const sectors = this.stocks
      .map((stock) => stock.sector)
      .filter((sector) => !!sector && sector !== "Unknown");
    return ["All sectors", ...new Set(sectors)].sort((left, right) => {
      if (left === "All sectors") return -1;
      if (right === "All sectors") return 1;
      return left.localeCompare(right);
    });
  }

  private readonly stocksState = signal<MarketStock[]>([]);
  get stocks(): MarketStock[] {
    return this.stocksState();
  }
  set stocks(value: MarketStock[]) {
    this.stocksState.set(value);
  }

  private readonly selectedState = signal<MarketStock>({
    id: 0,
    symbol: "",
    name: "",
    price: 0,
    change: 0,
    sector: "Unknown",
    cap: 0,
    color: "var(--purple)",
  });
  get selected(): MarketStock {
    return this.selectedState();
  }
  set selected(value: MarketStock) {
    this.selectedState.set(value);
  }

  private readonly historyState = signal<MarketDataEvent[]>([]);
  private readonly requestedHistoryKeyState = signal<string>("");
  private historyRequestId = 0;
  private readonly historyLoadingState = signal<boolean>(false);
  private readonly historyCache = new Map<string, MarketDataEvent[]>();
  private readonly historyRetryTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly historyRetryAttempts = new Map<string, number>();
  private readonly HISTORY_RETRY_DELAY_MS = 2500;
  private readonly HISTORY_RETRY_MAX_ATTEMPTS = 3;
  private readonly historyPrefetchInFlight = new Set<string>();
  private readonly fixedHistoryRanges = [
    "1d",
    "1w",
    "1m",
    "3m",
    "6m",
    "ytd",
    "1y",
    "5y",
  ] as const;
  private readonly historyRangeByLabel: Record<string, string> = {
    "1D": "1d",
    "1W": "1w",
    "1M": "1m",
    "3M": "3m",
    "6M": "6m",
    YTD: "ytd",
    "1Y": "1y",
    "5Y": "5y",
  };
  get history() {
    return this.historyState();
  }
  get historyLoading() {
    return this.historyLoadingState();
  }
  get priceLevels() {
    const events = this.historyState();
    if (events.length < 2) return ["", "", "", ""];
    const prices = events.map((e) => e.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const diff = max - min || 1;
    const yTicks = [30, 90, 150, 210];
    return yTicks.map((y) => `$${(min + ((220 - y) / 160) * diff).toFixed(2)}`);
  }

  private readonly queryState = signal<string>("");
  get query() {
    return this.queryState();
  }
  set query(value: string) {
    this.queryState.set(value);
    clearTimeout(this.searchDebounceTimer);
    const raw = (value || "").trim();
    const clean = (this.resolveSymbolFromQuery(raw) || raw).toUpperCase();
    if (
      clean.length >= 1 &&
      clean.length <= 20 &&
      /^[A-Z0-9.\-]+$/.test(clean)
    ) {
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
                  name: this.getCompanyNameForSymbol(clean),
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
                this.refreshMetadata([clean]);
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

  private readonly watchedState = signal<string[]>([]);
  private watchlistOwner: number | null = null;
  get watched() {
    return this.watchedState();
  }
  set watched(value: string[]) {
    this.watchedState.set(value);
  }

  constructor() {
    effect(() => {
      const token = this.session.token;
      const userId = this.session.currentUser?.id ?? null;
      untracked(() => {
        if (userId !== this.watchlistOwner) {
          this.watchlistOwner = userId;
          this.watched = userId === null ? [] : this.loadWatchlist();
        }
      });
      if (token) {
        untracked(() => {
          this.loadAssets();
          this.refreshHistory(this.selected.symbol, this.range);
        });
      }
    });

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
      if (this.scrollAnimationFrameId !== null && typeof window !== "undefined") {
        window.cancelAnimationFrame(this.scrollAnimationFrameId);
        this.scrollAnimationFrameId = null;
      }
      for (const timerId of this.historyRetryTimers.values()) {
        clearTimeout(timerId);
      }
      this.historyRetryTimers.clear();
      this.historyRetryAttempts.clear();
    });
  }

  loadWatchlist() {
    try {
      const v = JSON.parse(
        localStorage.getItem(`velocity-watchlist:${this.watchlistOwner}`) ||
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
      localStorage.setItem(
        `velocity-watchlist:${this.watchlistOwner}`,
        JSON.stringify(this.watched),
      );
    } catch {
      /* Keep the in-memory watchlist if browser storage is unavailable. */
    }
  }

  chart(s: MarketStock) {
    this.selected = s;
    this.refreshHistory(s.symbol, this.range, true);
    const onChartsPage = this.router.url.split("?")[0] === "/charts";
    if (onChartsPage) {
      this.scrollChartsToTop();
    }
    void this.router.navigateByUrl("/charts").then(() => {
      this.scrollChartsToTop();
    });
  }

  private scrollChartsToTop() {
    if (typeof window === "undefined") return;
    if (this.scrollAnimationFrameId !== null) {
      window.cancelAnimationFrame(this.scrollAnimationFrameId);
      this.scrollAnimationFrameId = null;
    }

    const startY = window.scrollY || window.pageYOffset || 0;
    if (startY <= 0) return;

    const durationMs = 520;
    const startTime = performance.now();
    const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);

    const tick = (timestamp: number) => {
      const elapsed = timestamp - startTime;
      const progress = Math.min(1, elapsed / durationMs);
      const eased = easeOutCubic(progress);
      const nextY = Math.round(startY * (1 - eased));
      window.scrollTo(0, nextY);

      if (progress < 1) {
        this.scrollAnimationFrameId = window.requestAnimationFrame(tick);
      } else {
        this.scrollAnimationFrameId = null;
        window.scrollTo(0, 0);
      }
    };

    this.scrollAnimationFrameId = window.requestAnimationFrame(tick);
  }

  loadAssets() {
    this.api.assets().subscribe({
      next: (assets) => {
        this.stocks = assets.map((asset) => ({
          ...this.parseAssetName(asset.name),
          id: asset.id,
          price: 0,
          change: 0,
          sector: "Unknown",
          cap: 0,
          color: "var(--purple)",
        }));
        if (this.stocks.length && !this.selected.symbol) {
          this.selected = this.stocks[0];
          this.refreshHistory(this.selected.symbol, this.range);
        }
        this.refreshMetadata(this.stocks.map((stock) => stock.symbol));
        this.refreshQuotes();
      },
      error: () => {},
    });
  }

  refreshQuotes() {
    if (!this.session.token) return;
    const symbols = [
      ...new Set([...this.stocks.map((s) => s.symbol), ...this.watched]),
    ];
    if (!symbols.length) return;
    this.api.latestQuotes(symbols).subscribe({
      next: (events) => {
        if (!Array.isArray(events) || !events.length) return;
        const eventMap = new Map(events.map((e) => [e.symbol, e]));
        const existing = new Set(this.stocks.map((s) => s.symbol));
        const fetched = events
          .filter((e) => !existing.has(e.symbol) && typeof e.price === "number")
          .map((e) => ({
            id: 0,
            symbol: e.symbol,
            name: this.getCompanyNameForSymbol(e.symbol),
            price: e.price,
            change: e.changePercent ?? 0,
            sector: "Unknown",
            cap: 0,
            color: "var(--purple)",
          }));
        this.stocks = [
          ...this.stocks.map((s) => {
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
          ...fetched,
        ];
        if (fetched.length) {
          this.refreshMetadata(fetched.map((stock) => stock.symbol));
        }
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

  refreshMetadata(symbols: string[]) {
    if (!this.session.token) return;
    this.api.marketMetadata(symbols).subscribe({
      next: (metadata) => this.applyMetadata(metadata),
      error: () => {},
    });
  }

  private applyMetadata(metadata: MarketSymbolMetadata[]) {
    if (!Array.isArray(metadata) || !metadata.length) return;
    const bySymbol = new Map(metadata.map((item) => [item.symbol, item]));
    this.stocks = this.stocks.map((stock) => {
      const item = bySymbol.get(stock.symbol);
      if (!item) return stock;
      return {
        ...stock,
        sector: item.sector || "Unknown",
        cap: Number(item.marketCap) || 0,
      };
    });
    const selectedMetadata = bySymbol.get(this.selected.symbol);
    if (selectedMetadata) {
      this.selected = {
        ...this.selected,
        sector: selectedMetadata.sector || "Unknown",
        cap: Number(selectedMetadata.marketCap) || 0,
      };
    }
  }

  getCompanyNameForSymbol(symbol: string) {
    const clean = (symbol || "").trim().toUpperCase();
    if (!clean) return "";
    const stock = this.stocks.find((item) => item.symbol.toUpperCase() === clean);
    return stock?.name || clean;
  }

  resolveSymbolFromQuery(query: string) {
    const raw = (query || "").trim();
    if (!raw) return "";

    const upper = raw.toUpperCase();
    if (/^[A-Z0-9.\-]{1,20}$/.test(upper)) return upper;

    const rawLower = raw.toLowerCase();
    const stockMatch = this.stocks.find((stock) => {
      const nameLower = (stock.name || "").toLowerCase();
      const symbolLower = (stock.symbol || "").toLowerCase();
      return (
        nameLower === rawLower ||
        nameLower.startsWith(rawLower) ||
        symbolLower === rawLower ||
        symbolLower.startsWith(rawLower)
      );
    });
    return stockMatch?.symbol?.toUpperCase() || "";
  }

  private parseAssetName(assetName: string): { symbol: string; name: string } {
    const raw = (assetName || "").trim();
    if (!raw) {
      return { symbol: "", name: "" };
    }

    const suffixMatch = raw.match(/\(([A-Z0-9.\-]{1,20})\)\s*$/i);
    if (!suffixMatch) {
      const symbol = raw.toUpperCase();
      return { symbol, name: this.getCompanyNameForSymbol(symbol) };
    }

    const symbol = suffixMatch[1].toUpperCase();
    const display = raw.replace(/\s*\([A-Z0-9.\-]{1,20}\)\s*$/i, "").trim();
    return { symbol, name: display || this.getCompanyNameForSymbol(symbol) };
  }

  refreshHistory(symbol: string, range: string, clearBeforeLoad = false) {
    if (!symbol || !this.session.token) return;
    const requestId = ++this.historyRequestId;
    const normalizedSymbol = symbol.trim().toUpperCase();
    const historyRange = this.normalizeHistoryRange(range);
    const cacheKey = `${normalizedSymbol}|${historyRange}`;
    this.requestedHistoryKeyState.set(cacheKey);
    const cached = this.historyCache.get(cacheKey);

    if (cached && cached.length >= 2) {
      this.historyState.set(cached);
      this.historyLoadingState.set(false);
      this.clearHistoryRetry(cacheKey);
    }

    const uncachedLoad = !cached;
    if (uncachedLoad) {
      this.historyState.set([]);
    }
    this.historyLoadingState.set(uncachedLoad);
    const to = new Date().toISOString();
    this.api.marketDataHistory(normalizedSymbol, historyRange, to).subscribe({
      next: (events) => {
        if (requestId !== this.historyRequestId) return;
        this.historyLoadingState.set(false);
        if (Array.isArray(events) && events.length >= 2) {
          this.clearHistoryRetry(cacheKey);
          this.historyCache.set(cacheKey, events);
          this.historyState.set(events);
          this.prefetchOtherHistoryRanges(normalizedSymbol, historyRange);
        } else {
          this.scheduleHistoryRetry(normalizedSymbol, historyRange, cacheKey);
          this.historyState.set([]);
        }
      },
      error: () => {
        if (requestId !== this.historyRequestId) return;
        this.scheduleHistoryRetry(normalizedSymbol, historyRange, cacheKey);
        this.historyState.set([]);
        if (this.requestedHistoryKeyState() === cacheKey) {
          this.historyLoadingState.set(
            (this.historyRetryAttempts.get(cacheKey) ?? 0) <
              this.HISTORY_RETRY_MAX_ATTEMPTS,
          );
        } else {
          this.historyLoadingState.set(false);
        }
      },
    });
  }

  private normalizeHistoryRange(range: string) {
    if (this.fixedHistoryRanges.includes(range as (typeof this.fixedHistoryRanges)[number])) {
      return range;
    }
    return this.historyRangeByLabel[range] ?? "1m";
  }

  private clearHistoryRetry(cacheKey: string) {
    const timerId = this.historyRetryTimers.get(cacheKey);
    if (timerId) {
      clearTimeout(timerId);
      this.historyRetryTimers.delete(cacheKey);
    }
    this.historyRetryAttempts.delete(cacheKey);
  }

  private scheduleHistoryRetry(symbol: string, historyRange: string, cacheKey: string) {
    if (this.historyRetryTimers.has(cacheKey)) {
      return;
    }

    const attempts = this.historyRetryAttempts.get(cacheKey) ?? 0;
    if (attempts >= this.HISTORY_RETRY_MAX_ATTEMPTS) {
      return;
    }

    this.historyRetryAttempts.set(cacheKey, attempts + 1);
    if (this.requestedHistoryKeyState() === cacheKey) {
      this.historyLoadingState.set(true);
    }
    const timerId = setTimeout(() => {
      this.historyRetryTimers.delete(cacheKey);
      if (!this.session.token) return;
      this.refreshHistory(symbol, historyRange);
    }, this.HISTORY_RETRY_DELAY_MS);
    this.historyRetryTimers.set(cacheKey, timerId);
  }

  private prefetchOtherHistoryRanges(symbol: string, requestedRange: string) {
    if (!symbol || !this.session.token) return;
    const to = new Date().toISOString();

    for (const historyRange of this.fixedHistoryRanges) {
      if (historyRange === requestedRange) continue;

      const cacheKey = `${symbol}|${historyRange}`;
      if (
        this.historyCache.has(cacheKey) ||
        this.historyPrefetchInFlight.has(cacheKey)
      ) {
        continue;
      }

      this.historyPrefetchInFlight.add(cacheKey);
      this.api.marketDataHistory(symbol, historyRange, to).subscribe({
        next: (events) => {
          if (Array.isArray(events) && events.length >= 2) {
            this.historyCache.set(cacheKey, events);
          }
        },
        error: () => {
          this.historyPrefetchInFlight.delete(cacheKey);
        },
        complete: () => {
          this.historyPrefetchInFlight.delete(cacheKey);
        },
      });
    }
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
