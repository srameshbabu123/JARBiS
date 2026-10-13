import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { PerformanceChartComponent } from "../../market/performance-chart";
import { MarketTableComponent } from "../../market/market-table";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-charts-page",
  imports: [
    CommonModule,
    FormsModule,
    PerformanceChartComponent,
    MarketTableComponent,
  ],
  templateUrl: "./charts-page.html",
})
export class ChartsPageComponent {}
