import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../workspace/workspace.service";
import { TradeComponent } from "../../trade/trade";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-trade-page",
  imports: [CommonModule, FormsModule, TradeComponent],
  templateUrl: "./trade-page.html",
})
export class TradePageComponent {
  protected readonly workspace = inject(WorkspaceService);
}
