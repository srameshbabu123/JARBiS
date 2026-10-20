import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
  OnChanges,
  OnInit,
  SimpleChanges,
  DestroyRef,
  ChangeDetectorRef,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { HttpErrorResponse } from "@angular/common/http";
import {
  Account,
  CreateOrder,
  Holding,
  Order,
  MarketDataEvent,
} from "../../../models/brokerage.models";
import { BrokerageApiService } from "../../../services/brokerage-api.service";
import { SessionService } from "../../../services/session.service";
import { MarketService } from "../../../services/market.service";

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-trade",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./trade.html",
  styleUrl: "./trade.css",
})
export class TradeComponent implements OnInit, OnChanges {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);
  @Input() account: Account | null = null;
  @Input() cash = 0;
  @Input() holdings: Holding[] = [];
  @Input() orders: Order[] = [];
  @Output() submitted = new EventEmitter<Order>();
  @Output() navigate = new EventEmitter<string>();
  readonly session = inject(SessionService);
  readonly market = inject(MarketService);
  api = inject(BrokerageApiService);

  side: "BUY" | "SELL" = "BUY";
  ticker = "";
  assetId: number | null = null;
  quantity: number | null = 1;
  price: number | null = null;
  reviewing: CreateOrder | null = null;
  submitting = false;
  error = "";
  receipt: Order | null = null;
  uncertain = false;

  private quoteRequest = 0;
  fetchingQuote = false;
  liveQuote: MarketDataEvent | null = null;

  popularTickers = [
    "AAPL",
    "NVDA",
    "MSFT",
    "TSLA",
    "GOOGL",
    "AMZN",
    "META",
    "AMD",
    "PLTR",
    "COIN",
    "SPY",
    "QQQ",
  ];

  ngOnInit() {
    this.fetchQuoteAndResolve(this.ticker);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes["holdings"] && this.side === "SELL") {
      if (
        this.holdingsChips.length > 0 &&
        !this.holdingsChips.some(
          (h) => h.ticker.toUpperCase() === this.ticker.toUpperCase(),
        )
      ) {
        this.selectTicker(this.holdingsChips[0].ticker);
      }
    }
  }

  get holdingsChips(): { ticker: string; quantity: number }[] {
    return this.holdings
      .filter((h) => h.quantity > 0)
      .map((h) => ({
        ticker: this.getHoldingTicker(h),
        quantity: h.quantity,
      }));
  }

  getHoldingTicker(h: Holding): string {
    const stock = this.market.stocks.find((s) => s.id === h.assetId);
    if (stock) return stock.symbol;

    const name = (h.assetName || "").trim();
    if (/^[A-Z]{1,5}$/i.test(name)) return name.toUpperCase();

    const parenMatch = name.match(/\(([A-Z]{1,6})\)/i);
    if (parenMatch) return parenMatch[1].toUpperCase();

    return name;
  }

  setSide(newSide: "BUY" | "SELL") {
    if (this.submitting) return;
    this.side = newSide;
    this.edit();

    if (newSide === "SELL") {
      if (
        this.holdingsChips.length > 0 &&
        !this.holdingsChips.some(
          (h) => h.ticker.toUpperCase() === this.ticker.toUpperCase(),
        )
      ) {
        this.selectTicker(this.holdingsChips[0].ticker);
        return;
      }
    }
    this.fetchQuoteAndResolve(this.ticker);
  }

  selectTicker(sym: string) {
    this.ticker = sym;
    this.onTickerChange(sym);
  }

  onTickerChange(value: string) {
    this.ticker = value;
    this.edit();
    this.fetchQuoteAndResolve(value);
  }

  private fetchQuoteAndResolve(rawTicker: string) {
    const requestId = ++this.quoteRequest;
    this.liveQuote = null;
    this.price = null;
    const raw = (rawTicker || "").trim();
    if (!raw) {
      this.assetId = null;
      this.liveQuote = null;
      return;
    }
    const rawLower = raw.toLowerCase();

    // 1. Resolve asset ID and canonical symbol
    const matchingHolding = this.holdings.find(
      (h) =>
        this.getHoldingTicker(h).toLowerCase() === rawLower ||
        (h.assetName && h.assetName.toLowerCase() === rawLower),
    );

    const matchingStock = this.market.stocks.find(
      (s) =>
        s.symbol.toLowerCase() === rawLower ||
        s.name.toLowerCase() === rawLower ||
        s.name.toLowerCase().startsWith(rawLower),
    );

    const sym = matchingHolding
      ? matchingHolding.assetName &&
        matchingHolding.assetName.toLowerCase() === rawLower
        ? this.getHoldingTicker(matchingHolding).toUpperCase()
        : raw.toUpperCase()
      : matchingStock
        ? matchingStock.symbol.toUpperCase()
        : raw.toUpperCase();

    if (matchingHolding) {
      this.assetId = matchingHolding.assetId;
    } else {
      this.assetId = null;
    }

    if (!this.connected) {
      if (matchingHolding) {
        this.price =
          matchingHolding.quantity > 0
            ? Math.round(
                (matchingHolding.marketValue / matchingHolding.quantity) * 100,
              ) / 100
            : matchingHolding.averagePrice || this.price;
      } else if (matchingStock) {
        this.price = matchingStock.price;
        this.liveQuote = {
          symbol: matchingStock.symbol,
          price: matchingStock.price,
          changePercent: matchingStock.change,
        } as MarketDataEvent;
      }
      this.fetchingQuote = false;
      this.changeDetector.markForCheck();
      return;
    }

    this.api
      .resolveAsset(sym)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (asset) => {
          if (requestId !== this.quoteRequest) return;
          if (!matchingHolding) {
            this.assetId = asset.id;
          }
          this.changeDetector.markForCheck();
        },
        error: () => {
          if (requestId !== this.quoteRequest) return;
          this.assetId = null;
          this.error =
            "This symbol is not available in the backend asset catalog.";
          this.changeDetector.markForCheck();
        },
      });

    // 2. Fetch live market data quote
    this.fetchingQuote = true;
    this.changeDetector.markForCheck();

    this.api
      .latestMarketData(sym)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (requestId !== this.quoteRequest) return;
          this.fetchingQuote = false;
          if (event && typeof event.price === "number") {
            this.liveQuote = event;
            this.price = Math.round(event.price * 100) / 100;
          }
          this.changeDetector.markForCheck();
        },
        error: () => {
          if (requestId !== this.quoteRequest) return;
          this.fetchingQuote = false;
          if (matchingHolding) {
            this.price =
              matchingHolding.quantity > 0
                ? Math.round(
                    (matchingHolding.marketValue / matchingHolding.quantity) *
                      100,
                  ) / 100
                : matchingHolding.averagePrice || this.price;
          } else if (matchingStock) {
            this.price = matchingStock.price;
          }
          this.changeDetector.markForCheck();
        },
      });
  }

  get connected() {
    return this.account !== null && this.session.currentUser !== null;
  }

  get currency() {
    return this.account?.currency || "USD";
  }

  get assetSymbol() {
    const raw = (this.ticker || "").trim();
    if (!raw) return "";
    const rawLower = raw.toLowerCase();

    const h = this.holdings.find(
      (item) =>
        this.getHoldingTicker(item).toLowerCase() === rawLower ||
        (item.assetName && item.assetName.toLowerCase() === rawLower),
    );
    if (h) return this.getHoldingTicker(h).toUpperCase();

    const stock = this.market.stocks.find(
      (s) =>
        s.symbol.toLowerCase() === rawLower ||
        s.name.toLowerCase() === rawLower ||
        s.name.toLowerCase().startsWith(rawLower),
    );
    if (stock) return stock.symbol.toUpperCase();

    return raw.toUpperCase();
  }

  get holding() {
    const sym = this.assetSymbol;
    const rawLower = (this.ticker || "").trim().toLowerCase();
    return this.holdings.find(
      (h) =>
        (this.assetId != null && h.assetId === this.assetId) ||
        this.getHoldingTicker(h).toUpperCase() === sym ||
        (h.assetName && h.assetName.toLowerCase() === rawLower),
    );
  }

  get assetLabel() {
    const sym = this.assetSymbol;
    const stock = this.market.stocks.find(
      (s) => s.symbol.toUpperCase() === sym,
    );
    if (stock) return `${stock.name} (${stock.symbol})`;
    const holding = this.holding;
    if (holding) return `${holding.assetName} (${sym})`;
    return sym ? `${sym} (Stock)` : "Enter a ticker";
  }

  get total() {
    return (this.quantity || 0) * (this.price || 0);
  }

  get pending() {
    return this.orders.filter(
      (o) => o.status === "PENDING" || o.status === "PARTIALLY_FILLED",
    );
  }

  get availableCash() {
    return Math.max(
      0,
      this.cash -
        this.pending
          .filter((o) => o.side === "BUY")
          .reduce(
            (n, o) => n + (o.remainingQuantity ?? o.quantity) * o.price,
            0,
          ),
    );
  }

  get availableShares() {
    const held = this.holding?.quantity || 0;
    const openSells = this.pending
      .filter((o) => o.side === "SELL" && o.asset?.id === this.assetId)
      .reduce((n, o) => n + (o.remainingQuantity ?? o.quantity), 0);
    return Math.max(0, held - openSells);
  }

  get validation() {
    if (!this.connected)
      return "Sign in and load your account before placing an order.";
    if (!this.ticker || !this.ticker.trim()) {
      return "Enter a stock ticker symbol.";
    }
    if (!Number.isSafeInteger(this.assetId) || Number(this.assetId) === 0) {
      return "Unable to resolve asset for this ticker.";
    }
    if (!Number.isFinite(this.quantity) || Number(this.quantity) <= 0) {
      return "Enter a quantity greater than zero.";
    }
    if (!Number.isFinite(this.price) || Number(this.price) <= 0) {
      return "Enter a price greater than zero.";
    }
    if (!Number.isFinite(this.total)) return "The order total is too large.";
    if (this.side === "BUY" && this.total > this.availableCash) {
      return "This order exceeds your estimated available cash.";
    }
    if (this.side === "SELL" && Number(this.quantity) > this.availableShares) {
      return "This order exceeds your available holdings.";
    }
    return "";
  }

  edit() {
    this.reviewing = null;
    this.receipt = null;
    this.error = "";
    this.uncertain = false;
  }

  review() {
    this.error = this.validation;
    if (this.error) return;
    this.receipt = null;
    this.reviewing = {
      accountId: this.account!.id,
      assetId: this.assetId!,
      side: this.side,
      quantity: this.quantity!,
      price: this.price!,
    };
  }

  confirm() {
    if (!this.reviewing || this.submitting || this.uncertain) return;
    if (this.validation) {
      this.error = this.validation;
      this.reviewing = null;
      return;
    }
    const request = { ...this.reviewing };
    this.submitting = true;
    this.error = "";

    if (this.account!.ownerId !== this.session.currentUser!.id) {
      this.submitting = false;
      this.error = "Load an account belonging to the signed-in user.";
      return;
    }

    this.api
      .createOrder(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => this.success(order),
        error: (e: HttpErrorResponse) => {
          this.changeDetector.markForCheck();
          this.submitting = false;

          if (e.status === 0 || e.status >= 500) {
            this.uncertain = true;
            this.error =
              "We could not confirm whether the order was accepted. Check order history before placing it again.";
          } else if (e.status === 401 || e.status === 403) {
            this.error =
              "Your session cannot place this order. Sign in again and reload your account.";
          } else {
            this.error =
              typeof e.error?.message === "string"
                ? e.error.message
                : "The order was rejected. Check the asset, account, quantity, and price.";
          }
        },
      });
  }

  private success(order: Order) {
    this.changeDetector.markForCheck();
    this.submitting = false;
    this.reviewing = null;
    this.receipt = order;
    this.submitted.emit(order);
  }
}
