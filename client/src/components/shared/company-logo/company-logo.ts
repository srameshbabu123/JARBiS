import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
} from "@angular/core";

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: "app-company-logo",
  standalone: true,
  template: `
    <span
      class="stock-icon"
      [style.color]="color"
      [style.--logo-bg]="badgeBackground"
      [attr.aria-label]="label + ' logo'"
    >
      @if(imageUrl && !failed) {
      <img
        [src]="imageUrl"
        [alt]="label + ' logo'"
        crossorigin="anonymous"
        (load)="onImageLoad($event)"
        (error)="failed = true"
      />
      } @else {
      <span aria-hidden="true">{{fallback}}</span>
      }
    </span>
  `,
  styles: [
    `:host { display: inline-block; height: 33px; width: 33px; flex-shrink: 0; }
     .stock-icon {
       width: 100%;
       height: 100%;
       overflow: hidden;
       display: inline-grid;
       place-items: center;
       border-radius: 10px;
       border: 1px solid color-mix(in srgb, var(--line) 60%, #ffffff);
       background: linear-gradient(
         145deg,
         color-mix(in srgb, var(--logo-bg, #f2f2f2) 94%, #ffffff),
         color-mix(in srgb, var(--logo-bg, #f2f2f2) 84%, #ffffff)
       );
       box-shadow: inset 0 1px 0 color-mix(in srgb, #ffffff 78%, transparent);
       font: 700 15px Manrope;
       color: var(--purple);
     }
     :host-context([data-theme="light"]) .stock-icon {
       border-color: color-mix(in srgb, var(--line) 90%, #ffffff);
       background: linear-gradient(
         145deg,
         color-mix(in srgb, #ffffff 82%, var(--surface)),
         color-mix(in srgb, #ffffff 70%, var(--surface))
       );
     }
     img { width: 100%; height: 100%; object-fit: contain; padding: 5px; display: block; }`,
  ],
})
export class CompanyLogoComponent implements OnChanges {
  @Input() symbol = "";
  @Input() name = "";
  @Input() color = "var(--purple)";
  failed = false;
  badgeBackground = "#f2f2f2";

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["symbol"] || changes["name"]) {
      this.failed = false;
      this.badgeBackground = "#f2f2f2";
    }
  }

  onImageLoad(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (!img) return;
    this.badgeBackground = this.sampleBackgroundColor(img) || "#f2f2f2";
  }

  private sampleBackgroundColor(img: HTMLImageElement): string | null {
    const canvas = document.createElement("canvas");
    const size = 16;
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) return null;

    try {
      ctx.drawImage(img, 0, 0, size, size);
      const { data } = ctx.getImageData(0, 0, size, size);
      let r = 0;
      let g = 0;
      let b = 0;
      let opaqueCount = 0;
      const total = size * size;

      for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
          const idx = (y * size + x) * 4;
          const alpha = data[idx + 3];
          if (alpha < 220) continue;
          r += data[idx];
          g += data[idx + 1];
          b += data[idx + 2];
          opaqueCount += 1;
        }
      }

      // Only tint the container when the logo is mostly opaque (usually logos with colored blocks).
      if (opaqueCount / total < 0.45) return null;

      return `rgb(${Math.round(r / opaqueCount)} ${Math.round(g / opaqueCount)} ${Math.round(b / opaqueCount)})`;
    } catch {
      return null;
    }
  }

  get label(): string {
    return this.name.trim() || this.symbol.trim() || "Company";
  }

  get fallback(): string {
    return (this.symbol.trim() || this.name.trim() || "?").slice(0, 1).toUpperCase();
  }

  get imageUrl(): string | null {
    const ticker = this.symbol.trim();
    const token = window.LOGO_DEV_TOKEN?.trim();
    if (
      !ticker ||
      !token ||
      (!token.startsWith("pk_") && !token.startsWith("live_"))
    ) {
      return null;
    }

    const isCrypto = /^[A-Z0-9]+-USD$/i.test(ticker);
    const lookupPath = isCrypto
      ? `crypto/${encodeURIComponent(ticker.split("-")[0])}`
      : `ticker/${encodeURIComponent(ticker)}`;
    const mode = document.documentElement.dataset["theme"] === "light" ? "light" : "dark";
    return `https://img.logo.dev/${lookupPath}?token=${encodeURIComponent(token)}&size=64&format=png&theme=${mode}`;
  }
}

declare global {
  interface Window {
    LOGO_DEV_TOKEN?: string;
  }
}
