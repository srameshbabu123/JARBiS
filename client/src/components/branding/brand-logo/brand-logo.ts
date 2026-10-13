import { ChangeDetectionStrategy, Component, input } from "@angular/core";
@Component({
  selector: "app-brand-logo",
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: "./brand-logo.html",
  styleUrl: "./brand-logo.css",
})
export class BrandLogoComponent {
  readonly compact = input(false);
}
