import { DOCUMENT } from "@angular/common";
import { Injectable, inject, signal } from "@angular/core";
type Theme = "dark" | "light";
@Injectable({ providedIn: "root" })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly mode = signal<Theme>(this.readPreference());
  constructor() {
    this.apply();
  }
  toggle() {
    this.mode.update((mode) => (mode === "dark" ? "light" : "dark"));
    this.apply();
    try {
      localStorage.setItem("velocity-theme", this.mode());
    } catch {
      /* The selected mode still works when storage is unavailable. */
    }
  }
  private readPreference(): Theme {
    try {
      return localStorage.getItem("velocity-theme") === "light"
        ? "light"
        : "dark";
    } catch {
      return "dark";
    }
  }
  private apply() {
    this.document.documentElement.dataset["theme"] = this.mode();
    const icon =
      this.document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (icon) icon.href = `assets/branding/velocity-icon-${this.mode()}.jpg`;
  }
}
