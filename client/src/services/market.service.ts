import { Injectable, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { STOCKS } from "../data/sample-market";
@Injectable({ providedIn: "root" })
export class MarketService {
  private readonly router = inject(Router);
  private readonly stocksState = signal<typeof STOCKS>(STOCKS);
  get stocks() {
    return this.stocksState();
  }
  set stocks(value: typeof STOCKS) {
    this.stocksState.set(value);
  }

  private readonly selectedState = signal<(typeof STOCKS)[number]>(STOCKS[0]);
  get selected() {
    return this.selectedState();
  }
  set selected(value: (typeof STOCKS)[number]) {
    this.selectedState.set(value);
  }

  private readonly queryState = signal<string>("");
  get query() {
    return this.queryState();
  }
  set query(value: string) {
    this.queryState.set(value);
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
  }

  private readonly watchedState = signal<string[]>(this.loadWatchlist());
  get watched() {
    return this.watchedState();
  }
  set watched(value: string[]) {
    this.watchedState.set(value);
  }

  loadWatchlist() {
    try {
      const v = JSON.parse(
        localStorage.getItem("velocity-watchlist") ||
          localStorage.getItem("jarbis-watchlist") ||
          '["NVDA","AAPL","TSLA","MSFT"]',
      );
      return Array.isArray(v) ? v.filter((x) => typeof x === "string") : [];
    } catch {
      return ["NVDA", "AAPL", "TSLA", "MSFT"];
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
  chart(s: (typeof STOCKS)[number]) {
    this.selected = s;
    void this.router.navigateByUrl("/charts");
  }
  get path() {
    const r = ["1D", "1W", "1M", "3M", "1Y", "ALL"].indexOf(this.range);
    return Array.from(
      { length: 80 },
      (_, i) =>
        `${i === 0 ? "M" : "L"} ${i * 10} ${205 - i * 1.65 + Math.sin(i * 1.7 + r) * 13 + Math.cos(i * 0.53 + r) * 20}`,
    ).join(" ");
  }
}
