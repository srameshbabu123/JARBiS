import { ChangeDetectionStrategy } from "@angular/core";
import { MarketService } from "../../../services/market.service";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-performance-chart",
  imports: [CommonModule, FormsModule],
  templateUrl: "./performance-chart.html",
})
export class PerformanceChartComponent {
  protected readonly market = inject(MarketService);
  protected readonly workspace = inject(WorkspaceService);
}
