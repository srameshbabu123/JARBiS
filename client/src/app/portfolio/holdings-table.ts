import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../workspace/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-holdings-table",
  imports: [CommonModule, FormsModule],
  templateUrl: "./holdings-table.html",
})
export class HoldingsTableComponent {
  protected readonly workspace = inject(WorkspaceService);
}
