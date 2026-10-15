import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { PortfolioSummaryComponent } from "../../components/portfolio/portfolio-summary/portfolio-summary";
import { PerformanceChartComponent } from "../../components/market/performance-chart/performance-chart";
import { HoldingsTableComponent } from "../../components/portfolio/holdings-table/holdings-table";
import { MarketNewsComponent } from "../../components/market/market-news/market-news";
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
