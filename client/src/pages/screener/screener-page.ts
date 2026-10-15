import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { MarketTableComponent } from "../../components/market/market-table/market-table";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-screener-page",
  imports: [CommonModule, FormsModule, MarketTableComponent],
  templateUrl: "./screener-page.html",
})
export class ScreenerPageComponent {}
