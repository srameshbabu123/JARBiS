import { ThemeService } from "./services/theme.service";
import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { RouterOutlet } from "@angular/router";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-root",
  imports: [RouterOutlet],
  template: "<router-outlet />",
})
export class App {
  private readonly theme = inject(ThemeService);
}
