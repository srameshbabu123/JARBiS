import { ChangeDetectionStrategy } from "@angular/core";
import { MarketService } from "./market.service";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../workspace/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-market-table",
  imports: [CommonModule, FormsModule],
  templateUrl: "./market-table.html",
})
export class MarketTableComponent {
  protected readonly market = inject(MarketService);
  protected readonly workspace = inject(WorkspaceService);
}
