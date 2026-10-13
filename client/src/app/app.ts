import { ChangeDetectionStrategy } from "@angular/core";
import { Component } from "@angular/core";
import { RouterOutlet } from "@angular/router";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-root",
  imports: [RouterOutlet],
  template: "<router-outlet />",
})
export class App {}
