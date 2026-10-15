import { ChangeDetectionStrategy, Component, inject } from "@angular/core";
import { ThemeService } from "../../../services/theme.service";
@Component({
  selector: "app-theme-toggle",
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./theme-toggle.html",
  styleUrl: "./theme-toggle.css",
})
export class ThemeToggleComponent {
  protected readonly theme = inject(ThemeService);
}
