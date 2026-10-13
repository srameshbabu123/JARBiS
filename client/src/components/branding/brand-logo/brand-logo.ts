import { ThemeService } from "../../../services/theme.service";
import {
  ChangeDetectionStrategy,
  Component,
  input,
  inject,
} from "@angular/core";
@Component({
  selector: "app-brand-logo",
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./brand-logo.html",
  styleUrl: "./brand-logo.css",
})
export class BrandLogoComponent {
  protected readonly theme = inject(ThemeService);
  readonly compact = input(false);
}
