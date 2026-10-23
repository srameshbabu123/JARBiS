import { ChangeDetectionStrategy, effect } from "@angular/core";
import { MarketService } from "../../../services/market.service";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
import { CompanyLogoComponent } from "../../shared/company-logo/company-logo";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-performance-chart",
  imports: [CommonModule, FormsModule, CompanyLogoComponent],
  templateUrl: "./performance-chart.html",
})
export class PerformanceChartComponent {
  protected readonly market = inject(MarketService);
  protected readonly workspace = inject(WorkspaceService);

  protected hoveredPoint: {
    index: number;
    x: number;
    y: number;
    price: number;
    timeLabel: string;
  } | null = null;

  constructor() {
    effect(() => {
      // Clear stale tooltip state whenever data granularity or series points change.
      this.market.range;
      this.market.history;
      this.hoveredPoint = null;
    });
  }

  protected onChartHover(event: MouseEvent): void {
    const events = this.market.history;
    if (events.length < 2) {
      this.hoveredPoint = null;
      return;
    }

    const svg = event.currentTarget as SVGSVGElement | null;
    if (!svg) {
      this.hoveredPoint = null;
      return;
    }

    const bounds = svg.getBoundingClientRect();
    if (!bounds.width || !bounds.height) {
      this.hoveredPoint = null;
      return;
    }

    const localX = event.clientX - bounds.left;
    const localY = event.clientY - bounds.top;
    if (
      localX < 0 ||
      localX > bounds.width ||
      localY < 0 ||
      localY > bounds.height
    ) {
      this.hoveredPoint = null;
      return;
    }

    const index = Math.max(
      0,
      Math.min(
        events.length - 1,
        Math.round((localX / bounds.width) * (events.length - 1)),
      ),
    );

    const point = this.toChartPoint(index, events);
    this.hoveredPoint = {
      index,
      x: point.x,
      y: point.y,
      price: events[index].price,
      timeLabel: this.formatTimeLabel(events[index].asOf || events[index].capturedAt),
    };
  }

  protected clearChartHover(): void {
    this.hoveredPoint = null;
  }

  private toChartPoint(index: number, events: { price: number }[]): { x: number; y: number } {
    const prices = events.map((entry) => entry.price);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const diff = max - min || 1;
    const x = (index / (events.length - 1)) * 790;
    const y = 220 - ((events[index].price - min) / diff) * 160;
    return { x, y };
  }

  private formatTimeLabel(raw: string | undefined): string {
    if (!raw) return "Time unavailable";
    const value = new Date(raw);
    if (Number.isNaN(value.getTime())) return "Time unavailable";
    if (this.market.range !== "1D") {
      return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
      }).format(value);
    }
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(value);
  }
}
