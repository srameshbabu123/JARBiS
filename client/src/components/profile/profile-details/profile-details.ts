import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-profile-details",
  imports: [CommonModule, FormsModule],
  templateUrl: "./profile-details.html",
})
export class ProfileDetailsComponent implements OnInit {
  ngOnInit() {
    this.workspace.discoverAccounts();
  }
  protected readonly workspace = inject(WorkspaceService);
}
