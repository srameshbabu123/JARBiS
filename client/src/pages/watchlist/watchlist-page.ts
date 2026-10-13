import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { MarketTableComponent } from "../../components/market/market-table/market-table";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-watchlist-page",
  imports: [CommonModule, FormsModule, MarketTableComponent],
  templateUrl: "./watchlist-page.html",
})
export class WatchlistPageComponent {}
