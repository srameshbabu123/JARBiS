import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { MarketNewsComponent } from "../../market/market-news";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-news-page",
  imports: [CommonModule, FormsModule, MarketNewsComponent],
  templateUrl: "./news-page.html",
})
export class NewsPageComponent {}
