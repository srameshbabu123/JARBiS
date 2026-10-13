import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { PortfolioSummaryComponent } from "../../portfolio/portfolio-summary";
import { PerformanceChartComponent } from "../../market/performance-chart";
import { HoldingsTableComponent } from "../../portfolio/holdings-table";
import { MarketNewsComponent } from "../../market/market-news";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-overview-page",
  imports: [
    CommonModule,
    FormsModule,
    PortfolioSummaryComponent,
    PerformanceChartComponent,
    HoldingsTableComponent,
    MarketNewsComponent,
  ],
  templateUrl: "./overview-page.html",
})
export class OverviewPageComponent {}
