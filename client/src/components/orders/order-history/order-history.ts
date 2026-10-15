import { ChangeDetectionStrategy, signal } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
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
  protected disputeOrderId: number | null = null;
  protected disputeReason = "";
  protected disputeDetails = "";
  protected readonly disputeReasons = [
    "Incorrect quantity",
    "Incorrect price",
    "Order status issue",
    "Unrecognized order",
    "Other",
  ];
  protected resetDispute() {
    this.disputeOrderId = null;
    this.disputeReason = "";
    this.disputeDetails = "";
    this.disputeOpen.set(false);
  }
  get history() {
    return this.workspace.orders.filter(
      (o) => this.orderFilter === "All" || o.status === this.orderFilter,
    );
  }
  protected readonly workspace = inject(WorkspaceService);
}
