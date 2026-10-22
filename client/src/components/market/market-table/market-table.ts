import { ChangeDetectionStrategy } from "@angular/core";
import { MarketService } from "../../../services/market.service";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";

type SortColumn = "symbol" | "price" | "change" | "sector" | "cap";

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-market-table",
  imports: [CommonModule, FormsModule],
  templateUrl: "./market-table.html",
})
export class MarketTableComponent {
  protected readonly pageSize = 25;
  protected page = 1;
  protected sortBy: SortColumn | "none" = "none";
  protected sortDirection: "desc" | "asc" = "desc";
  protected readonly market = inject(MarketService);
  protected readonly workspace = inject(WorkspaceService);

  protected get supportsPaginationAndSorting() {
    return this.workspace.page === "screener" || this.workspace.page === "charts";
  }

  protected get pagedRows() {
    const rows = this.sortedRows;
    if (!this.supportsPaginationAndSorting) return rows;
    const start = (this.currentPage - 1) * this.pageSize;
    return rows.slice(start, start + this.pageSize);
  }

  protected get currentPage() {
    return Math.min(this.page, this.totalPages);
  }

  protected get totalRows() {
    return this.sortedRows.length;
  }

  protected get totalPages() {
    if (!this.supportsPaginationAndSorting) return 1;
    return Math.max(1, Math.ceil(this.totalRows / this.pageSize));
  }

  protected toggleSort(column: SortColumn) {
    if (this.sortBy === column) {
      this.sortDirection = this.sortDirection === "desc" ? "asc" : "desc";
    } else {
      this.sortBy = column;
      this.sortDirection = "desc";
    }
    this.page = 1;
  }

  protected prevPage() {
    if (this.currentPage > 1) this.page = this.currentPage - 1;
  }

  protected nextPage() {
    if (this.currentPage < this.totalPages) this.page = this.currentPage + 1;
  }

  protected formatMarketCap(cap: number) {
    if (!Number.isFinite(cap) || cap <= 0) return "—";
    if (cap >= 1_000_000_000_000) {
      return `${(cap / 1_000_000_000_000).toFixed(2)}T`;
    }
    if (cap >= 1_000_000_000) {
      return `${(cap / 1_000_000_000).toFixed(2)}B`;
    }
    if (cap >= 1_000_000) {
      return `${(cap / 1_000_000).toFixed(2)}M`;
    }
    return cap.toLocaleString();
  }

  protected isSorted(column: SortColumn) {
    return this.sortBy === column;
  }

  protected sortIndicator(column: SortColumn) {
    if (this.sortBy !== column) return "↕";
    return this.sortDirection === "desc" ? "↓" : "↑";
  }

  private get sortedRows() {
    const rows = this.market.filtered.filter(
      (stock) =>
        this.workspace.page !== "watchlist" ||
        this.market.watched.includes(stock.symbol),
    );
    const sortColumn = this.sortBy;
    const direction = this.sortDirection;
    if (sortColumn === "none") return rows;

    const sorted = [...rows].sort((left, right) => {
      const [a, b] = this.valuesForColumn(left, right, sortColumn);
      if (typeof a === "number" && typeof b === "number") {
        return a - b;
      }
      return String(a).localeCompare(String(b));
    });

    return direction === "asc" ? sorted : sorted.reverse();
  }

  private valuesForColumn(left: any, right: any, column: SortColumn): [number | string, number | string] {
    switch (column) {
      case "symbol":
        return [left.symbol || "", right.symbol || ""];
      case "price":
        return [left.price || 0, right.price || 0];
      case "change":
        return [left.change || 0, right.change || 0];
      case "sector":
        return [left.sector || "Unknown", right.sector || "Unknown"];
      case "cap":
        return [left.cap || 0, right.cap || 0];
      default:
        return ["", ""];
    }
  }
}
