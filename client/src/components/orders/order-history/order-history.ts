import { ChangeDetectionStrategy, effect, signal } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { catchError, finalize, forkJoin, of } from "rxjs";
import { WorkspaceService } from "../../../services/workspace.service";
import { OrderDispute } from "../../../models/brokerage.models";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: "./order-history.css",
  selector: "app-order-history",
  imports: [CommonModule, FormsModule],
  templateUrl: "./order-history.html",
})
export class OrderHistoryComponent {
  orderFilter = "All";
  protected readonly disputeOpen = signal(false);
  protected readonly disputeNotice = signal("");
  protected readonly disputeSubmitting = signal(false);
  protected readonly disputesLoading = signal(false);
  protected readonly openDisputesLoading = signal(false);
  protected readonly ordersRefreshing = signal(false);
  protected readonly cancellingOrderId = signal<number | null>(null);
  protected readonly disputeHistory = signal<OrderDispute[]>([]);
  protected readonly openDisputes = signal<OrderDispute[]>([]);
  protected disputeOrderId: number | null = null;
  protected disputeReason = "";
  protected disputeDetails = "";
  private lastLoadedOrderSignature = "";
  protected readonly disputeReasons = [
    "Incorrect quantity",
    "Incorrect price",
    "Order status issue",
    "Unrecognized order",
    "Other",
  ];

  constructor() {
    effect(() => {
      const signature = this.workspace.orders
        .map((order) => order.id)
        .sort((a, b) => a - b)
        .join(",");
      if (signature === this.lastLoadedOrderSignature) return;
      this.lastLoadedOrderSignature = signature;
      this.loadOpenDisputes();
    });
  }

  protected resetDispute() {
    this.disputeOrderId = null;
    this.disputeReason = "";
    this.disputeDetails = "";
    this.disputeHistory.set([]);
    this.disputeNotice.set("");
    this.disputeOpen.set(false);
  }

  protected toggleDisputePanel() {
    const next = !this.disputeOpen();
    this.disputeOpen.set(next);
    if (next) {
      this.loadOpenDisputes();
    }
  }

  protected refreshOrderStatus() {
    const account = this.workspace.loadedAccount;
    if (!account || this.ordersRefreshing()) return;

    this.ordersRefreshing.set(true);
    this.workspace.api
      .orders(account.id)
      .pipe(finalize(() => this.ordersRefreshing.set(false)))
      .subscribe({
        next: (orders) => {
          this.workspace.orders = orders;
          this.workspace.notice = "Order status refreshed.";
          this.loadOpenDisputes();
        },
        error: () => {
          this.workspace.error =
            "Could not refresh order history. Please try again.";
        },
      });
  }

  protected cancelOrder(orderId: number) {
    if (this.cancellingOrderId() !== null) return;

    this.cancellingOrderId.set(orderId);
    this.workspace.api
      .cancelOrder(orderId)
      .pipe(finalize(() => this.cancellingOrderId.set(null)))
      .subscribe({
        next: (cancelled) => {
          this.workspace.orders = this.workspace.orders.map((order) =>
            order.id === cancelled.id ? cancelled : order,
          );
          this.workspace.notice = `Order #${cancelled.id} cancelled.`;
        },
        error: (error) => {
          this.workspace.error =
            typeof error?.error?.message === "string"
              ? error.error.message
              : "Could not cancel this order. Refresh status and try again.";
        },
      });
  }

  protected onDisputeOrderChange(orderId: number | null) {
    this.disputeOrderId = orderId;
    this.disputeNotice.set("");
    this.disputeHistory.set([]);
    if (orderId === null) return;

    this.disputesLoading.set(true);
    this.workspace.api
      .orderDisputes(orderId)
      .pipe(finalize(() => this.disputesLoading.set(false)))
      .subscribe({
        next: (disputes) => this.disputeHistory.set(disputes),
        error: () =>
          this.disputeNotice.set(
            "Could not load existing disputes for this order. You can still submit a new one.",
          ),
      });
  }

  protected loadOpenDisputes() {
    const orderIds = [...new Set(this.workspace.orders.map((order) => order.id))];
    if (!orderIds.length) {
      this.openDisputes.set([]);
      return;
    }

    this.openDisputesLoading.set(true);
    forkJoin(
      orderIds.map((orderId) =>
        this.workspace.api
          .orderDisputes(orderId)
          .pipe(catchError(() => of([] as OrderDispute[]))),
      ),
    )
      .pipe(finalize(() => this.openDisputesLoading.set(false)))
      .subscribe((all) => {
        const open = all
          .flat()
          .filter((item) => (item.status || "").toUpperCase() === "OPEN")
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
        this.openDisputes.set(open);
      });
  }
  protected get canSubmitDispute() {
    return (
      this.disputeOrderId !== null &&
      !!this.disputeReason.trim() &&
      !!this.disputeDetails.trim() &&
      !this.disputeSubmitting()
    );
  }
  protected submitDispute() {
    if (!this.canSubmitDispute) {
      this.disputeNotice.set(
        "Select an order, choose a reason, and add details before submitting.",
      );
      return;
    }
    if (this.disputeSubmitting()) return;

    const orderId = this.disputeOrderId;
    if (orderId === null) return;

    this.disputeNotice.set("");
    this.disputeSubmitting.set(true);
    this.workspace.api
      .submitOrderDispute(orderId, {
        reason: this.disputeReason.trim(),
        details: this.disputeDetails.trim(),
      })
      .pipe(finalize(() => this.disputeSubmitting.set(false)))
      .subscribe({
        next: (dispute) => {
          this.disputeNotice.set(
            `Dispute #${dispute.id} submitted successfully and is now ${dispute.status}.`,
          );
          this.disputeHistory.update((current) => [dispute, ...current]);
          this.openDisputes.update((current) => [dispute, ...current]);
          this.disputeReason = "";
          this.disputeDetails = "";
        },
        error: (error) => {
          const message =
            typeof error?.error?.message === "string"
              ? error.error.message
              : "Unable to submit dispute right now. Please try again.";
          this.disputeNotice.set(message);
        },
      });
  }
  get history() {
    return this.workspace.orders.filter(
      (o) => this.orderFilter === "All" || o.status === this.orderFilter,
    );
  }
  protected readonly workspace = inject(WorkspaceService);
}
