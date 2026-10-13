import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../workspace/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-portfolio-summary",
  imports: [CommonModule, FormsModule],
  templateUrl: "./portfolio-summary.html",
})
export class PortfolioSummaryComponent {
  protected readonly workspace = inject(WorkspaceService);
}
