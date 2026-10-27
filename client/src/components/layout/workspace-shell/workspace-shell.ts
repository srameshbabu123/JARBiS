import { ThemeToggleComponent } from "../../branding/theme-toggle/theme-toggle";
import { BrandLogoComponent } from "../../branding/brand-logo/brand-logo";
import { ChangeDetectionStrategy, signal } from "@angular/core";
import { MarketService } from "../../../services/market.service";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../../services/workspace.service";
import { RouterOutlet } from "@angular/router";
import { RouterLink } from "@angular/router";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-workspace-shell",
  imports: [
    ThemeToggleComponent,
    BrandLogoComponent,
    CommonModule,
    FormsModule,
    RouterOutlet,
    RouterLink,
  ],
  templateUrl: "./workspace-shell.html",
})
export class WorkspaceShellComponent {
  protected readonly market = inject(MarketService);
  protected readonly workspace = inject(WorkspaceService);
  protected readonly menuOpen = signal(false);

  toggleMenu() {
    this.menuOpen.update((v) => !v);
  }

  closeMenu() {
    this.menuOpen.set(false);
  }
}
