import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { HttpErrorResponse } from "@angular/common/http";
import { finalize } from "rxjs";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { WorkspaceService } from "../../../services/workspace.service";
import { BrokerageApiService } from "../../../services/brokerage-api.service";

interface CashReview {
  accountId: number | null;
  amount: number;
  action: "deposit" | "withdraw";
  currency: string;
}
@Component({
  selector: "app-cash-transfer",
  imports: [CommonModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./cash-transfer.html",
  styleUrl: "./cash-transfer.css",
})
export class CashTransferComponent {
  protected readonly workspace = inject(WorkspaceService);
  private readonly api = inject(BrokerageApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly amount = signal<number | null>(null);
  protected readonly action = signal<"deposit" | "withdraw">("deposit");
  protected readonly reviewing = signal<CashReview | null>(null);
  protected readonly submitting = signal(false);
  protected readonly error = signal("");
  protected readonly receipt = signal("");
  protected readonly uncertain = signal(false);
  private accountId: number | null = null;
  constructor() {
    effect(() => {
      const id = this.workspace.loadedAccount?.id ?? null;
      if (id !== this.accountId) {
        this.accountId = id;
        this.reset();
      }
    });
  }
  protected get currency() {
    return this.workspace.loadedAccount?.currency ?? "USD";
  }
  protected get unavailable() {
    return (
      !!this.workspace.session.currentUser && !this.workspace.loadedAccount
    );
  }
  protected get validation() {
    const amount = this.amount();
    if (
      amount === null ||
      !Number.isFinite(amount) ||
      amount <= 0 ||
      !Number.isSafeInteger(Math.round(amount * 100)) ||
      Math.abs(amount * 100 - Math.round(amount * 100)) > 1e-6
    )
      return "Enter a positive amount with at most two decimal places.";
    if (this.action() === "withdraw" && amount > this.workspace.availableCash)
      return "Amount exceeds cash available after open orders.";
    return "";
  }
  protected choose(action: "deposit" | "withdraw") {
    this.action.set(action);
    this.reset();
  }
  protected reset() {
    this.reviewing.set(null);
    this.error.set("");
    this.receipt.set("");
    this.uncertain.set(false);
    this.amount.set(null);
  }
  protected review() {
    if (this.submitting() || this.unavailable || this.workspace.busy) return;
    this.error.set(this.validation);
    if (this.validation) return;
    this.receipt.set("");
    this.reviewing.set({
      accountId: this.workspace.loadedAccount?.id ?? null,
      amount: this.amount()!,
      action: this.action(),
      currency: this.currency,
    });
  }
  protected confirm() {
    const review = this.reviewing();
    if (!review || this.submitting() || this.uncertain() || this.unavailable)
      return;
    if (
      review.accountId !== (this.workspace.loadedAccount?.id ?? null) ||
      this.validation
    ) {
      this.error.set(
        this.validation || "Your account changed. Review the amount again.",
      );
      this.reviewing.set(null);
      return;
    }
    if (review.accountId === null) {
      this.workspace.cash =
        Math.round(
          (this.workspace.cash +
            (review.action === "deposit" ? review.amount : -review.amount)) *
            100,
        ) / 100;
      this.complete(review, true);
      return;
    }
    this.submitting.set(true);
    this.workspace.cashBusy.set(true);
    this.error.set("");
    this.api
      .changeCash(review.accountId, review.amount, review.action)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.submitting.set(false);
          this.workspace.cashBusy.set(false);
        }),
      )
      .subscribe({
        next: (account) => {
          this.workspace.applyCash(account);
          this.complete(review, false);
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 0 || error.status >= 500) {
            this.uncertain.set(true);
            this.error.set(
              "We could not confirm this cash change. Check your account balance before starting another transfer. Do not submit this request again.",
            );
          } else {
            this.error.set(
              typeof error.error?.message === "string"
                ? error.error.message
                : "Cash change was rejected. Reload your account and check the amount.",
            );
          }
        },
      });
  }
  private complete(review: CashReview, demo: boolean) {
    this.reviewing.set(null);
    this.amount.set(null);
    this.receipt.set(
      `${demo ? "Demo " : ""}${review.action === "deposit" ? "Deposit" : "Withdrawal"} completed. Your cash balance has been updated.`,
    );
  }
}
