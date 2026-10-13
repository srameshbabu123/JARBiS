import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { OrderHistoryComponent } from "../../components/orders/order-history/order-history";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-order-history-page",
  imports: [CommonModule, FormsModule, OrderHistoryComponent],
  templateUrl: "./order-history-page.html",
})
export class OrderHistoryPageComponent {}
