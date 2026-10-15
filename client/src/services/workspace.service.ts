import { toSignal } from "@angular/core/rxjs-interop";
import { Injectable, inject, signal } from "@angular/core";
import { Router, NavigationEnd } from "@angular/router";
import { forkJoin, filter, map, Subject, takeUntil, finalize } from "rxjs";
import { BrokerageApiService } from "./brokerage-api.service";
import { Holding, Order, Account } from "../models/brokerage.models";
import { SessionService } from "./session.service";
import { STOCKS } from "../data/sample-market";
@Injectable({ providedIn: "root" })
export class WorkspaceService {
  readonly api = inject(BrokerageApiService);
  readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly resetRequests = new Subject<void>();
  private readonly currentPage = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(
        (event) => event.urlAfterRedirects.split("?")[0].slice(1) || "overview",
      ),
    ),
    { initialValue: this.router.url.split("?")[0].slice(1) || "overview" },
  );
  get page() {
    return this.currentPage();
  }

  readonly nav = [
    "Overview",
    "Trade",
    "Charts",
    "Watchlist",
    "Screener",
    "News",
    "Order history",
    "Profile",
  ];
  readonly icons = ["◈", "⇄", "⌁", "☆", "⊞", "▤", "↔", "◉"];
  private readonly errorState = signal<string>("");
  get error() {
    return this.errorState();
  }
  set error(value: string) {
    this.errorState.set(value);
  }
  private readonly noticeState = signal<string>("");
  get notice() {
    return this.noticeState();
  }
  set notice(value: string) {
    this.noticeState.set(value);
  }
  private readonly busyState = signal<boolean>(false);
  get busy() {
    return this.busyState();
  }
  set busy(value: boolean) {
    this.busyState.set(value);
  }
  private readonly liveState = signal<boolean>(false);
  get live() {
    return this.liveState();
  }
  set live(value: boolean) {
    this.liveState.set(value);
  }
  private readonly userIdState = signal<number>(1);
  get userId() {
    return this.userIdState();
  }
  set userId(value: number) {
    this.userIdState.set(value);
  }
  private readonly accountIdState = signal<number>(1);
  get accountId() {
    return this.accountIdState();
  }
  set accountId(value: number) {
    this.accountIdState.set(value);
  }
  private readonly profileNameState = signal<string>("Alex Morgan");
  get profileName() {
    return this.profileNameState();
  }
  set profileName(value: string) {
    this.profileNameState.set(value);
  }
  private readonly profileEmailState = signal<string>("alex@example.com");
  get profileEmail() {
    return this.profileEmailState();
  }
  set profileEmail(value: string) {
    this.profileEmailState.set(value);
  }
  private readonly cashState = signal<number>(12450);
  get cash() {
    return this.cashState();
  }
  set cash(value: number) {
    this.cashState.set(value);
  }
  private readonly holdingsState = signal<Holding[]>([]);
  get holdings() {
    return this.holdingsState();
  }
  set holdings(value: Holding[]) {
    this.holdingsState.set(value);
  }
  private readonly ordersState = signal<Order[]>([]);
  get orders() {
    return this.ordersState();
  }
  set orders(value: Order[]) {
    this.ordersState.set(value);
  }
  private readonly loadedAccountState = signal<Account | null>(null);
  get loadedAccount() {
    return this.loadedAccountState();
  }
  set loadedAccount(value: Account | null) {
    this.loadedAccountState.set(value);
  }
  readonly accounts = signal<Account[]>([]);
  readonly accountsLoading = signal(false);
  readonly cashBusy = signal(false);
  get currency() {
    return this.loadedAccount?.currency ?? "USD";
  }
  get availableCash() {
    return Math.max(
      0,
      this.cash -
        this.orders
          .filter(
            (o) =>
              o.side === "BUY" &&
              ["PENDING", "PARTIALLY_FILLED"].includes(o.status),
          )
          .reduce(
            (total, o) => total + (o.remainingQuantity ?? o.quantity) * o.price,
            0,
          ),
    );
  }
  discoverAccounts() {
    if (!this.session.currentUser || this.accountsLoading()) return;
    this.accountsLoading.set(true);
    this.error = "";
    this.api
      .myAccounts()
      .pipe(
        takeUntil(this.resetRequests),
        finalize(() => this.accountsLoading.set(false)),
      )
      .subscribe({
        next: (accounts) => {
          this.accounts.set(accounts);
          if (accounts.length === 1 && !this.loadedAccount)
            this.selectAccount(accounts[0].id);
        },
        error: () =>
          (this.error =
            "Could not load your accounts. Please refresh your account list."),
      });
  }
  selectAccount(id: number) {
    if (this.cashBusy() || this.busy) return;
    this.accountId = id;
    this.connect();
  }
  applyCash(account: Account) {
    if (this.loadedAccount?.id !== account.id) return;
    this.loadedAccount = account;
    this.cash = account.balance;
    this.accounts.update((accounts) =>
      accounts.map((a) => (a.id === account.id ? account : a)),
    );
  }
  constructor() {
    this.resetDemo();
  }

  go(p: string) {
    void this.router.navigateByUrl("/" + p.toLowerCase().replace(" ", "-"));
    this.notice = "";
    this.error = "";
  }
  resetDemo() {
    this.resetRequests.next();
    this.accounts.set([]);
    this.cashBusy.set(false);
    this.busy = false;
    this.loadedAccount = null;
    this.live = false;
    this.profileName = "Alex Morgan";
    this.profileEmail = "alex@example.com";
    this.userId = 1;
    this.accountId = 1;
    this.cash = 12450;
    this.holdings = STOCKS.slice(0, 4).map((s, i) => {
      const q = [80, 45, 30, 25][i],
        a = s.price * 0.86;
      return {
        id: i + 1,
        accountId: 1,
        assetId: i + 1,
        assetName: s.name,
        quantity: q,
        averagePrice: a,
        marketValue: q * s.price,
        costBasis: q * a,
        unrealizedPnL: q * (s.price - a),
        unrealizedPnLPercentage: 16.28,
        isProfitable: true,
      };
    });
    this.orders = STOCKS.slice(0, 5).map((s, i) => ({
      id: 1048 - i,
      asset: { id: i + 1, name: s.name, symbol: s.symbol },
      side: i === 3 ? "SELL" : "BUY",
      quantity: [10, 5, 12, 8, 15][i],
      price: s.price,
      status: i === 0 ? "PENDING" : i === 4 ? "CANCELLED" : "COMPLETED",
    }));
  }
  get invested() {
    return this.holdings.reduce((a, h) => a + h.marketValue, 0);
  }
  get pnl() {
    return this.holdings.reduce((a, h) => a + h.unrealizedPnL, 0);
  }

  signOut() {
    this.session.signOut();
    this.resetDemo();
    this.go("Login");
  }
  orderSubmitted(order: Order) {
    this.orders = [order, ...this.orders.filter((o) => o.id !== order.id)];
  }
  refreshOrders() {
    if (!this.loadedAccount) return;
    this.api
      .orders(this.loadedAccount.id)
      .pipe(takeUntil(this.resetRequests))
      .subscribe({
        next: (orders) => {
          this.orders = orders;
          this.notice = "Order status refreshed.";
        },
        error: () =>
          (this.error = "Could not refresh order history. Please try again."),
      });
  }
  connect() {
    if (this.busy) return;
    this.error = "";
    if (!this.session.currentUser) {
      this.error = "Sign in before loading an account.";
      return;
    }
    this.userId = this.session.currentUser.id;
    if (
      !Number.isInteger(this.userId) ||
      !Number.isInteger(this.accountId) ||
      this.userId < 1 ||
      this.accountId < 1 ||
      !this.accounts().some((account) => account.id === this.accountId)
    ) {
      this.error = "Choose one of your brokerage accounts.";
      return;
    }
    this.busy = true;
    forkJoin({
      user: this.api.user(this.userId),
      account: this.api.account(this.accountId),
      holdings: this.api.holdings(this.accountId),
      orders: this.api.orders(this.accountId),
    })
      .pipe(
        takeUntil(this.resetRequests),
        finalize(() => (this.busy = false)),
      )
      .subscribe({
        next: (r) => {
          this.busy = false;
          if (r.account.ownerId !== r.user.id) {
            this.error = "This account does not belong to the selected user.";
            return;
          }
          this.loadedAccount = r.account;
          this.accounts.update((accounts) =>
            accounts.map((account) =>
              account.id === r.account.id ? r.account : account,
            ),
          );
          this.live = true;
          this.profileName = r.user.fullName;
          this.profileEmail = r.user.email;
          this.cash = r.account.balance;
          this.holdings = r.holdings;
          this.orders = r.orders;
          this.notice =
            "Loaded account " + r.account.id + " · " + r.account.currency;
        },
        error: () => {
          this.busy = false;
          this.error =
            "Unable to load account. Check API connection and IDs. Your previous account view remains visible.";
        },
      });
  }
  saveProfile() {
    if (!this.live) {
      this.notice = "Demo profile updated for this preview.";
      return;
    }
    this.api
      .updateUser(this.userId, {
        fullName: this.profileName,
        email: this.profileEmail,
      })
      .pipe(takeUntil(this.resetRequests))
      .subscribe({
        next: () => (this.notice = "Profile saved."),
        error: () => (this.error = "Profile could not be saved."),
      });
  }
}
