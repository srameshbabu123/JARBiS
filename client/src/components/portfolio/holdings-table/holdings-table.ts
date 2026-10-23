import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
import { MarketService } from "../../../services/market.service";
import { CompanyLogoComponent } from "../../shared/company-logo/company-logo";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-holdings-table",
  imports: [CommonModule, FormsModule, CompanyLogoComponent],
  templateUrl: "./holdings-table.html",
})
export class HoldingsTableComponent {
  protected readonly workspace = inject(WorkspaceService);
  protected readonly market = inject(MarketService);

  protected symbolForHolding(assetId: number, fallback: string): string {
    return this.market.stocks.find((stock) => stock.id === assetId)?.symbol || fallback;
  }
}
