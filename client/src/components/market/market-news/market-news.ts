import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-market-news",
  imports: [CommonModule, FormsModule],
  templateUrl: "./market-news.html",
})
export class MarketNewsComponent {
  protected readonly workspace = inject(WorkspaceService);
}
