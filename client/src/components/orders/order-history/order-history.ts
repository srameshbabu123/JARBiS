import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-order-history",
  imports: [CommonModule, FormsModule],
  templateUrl: "./order-history.html",
})
export class OrderHistoryComponent {
  orderFilter = "All";
  get history() {
    return this.workspace.orders.filter(
      (o) => this.orderFilter === "All" || o.status === this.orderFilter,
    );
  }
  protected readonly workspace = inject(WorkspaceService);
}
