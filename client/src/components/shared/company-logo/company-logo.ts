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
    <span class="stock-icon" [style.color]="color" [attr.aria-label]="label + ' logo'">
      @if(imageUrl && !failed) {
      <img [src]="imageUrl" [alt]="label + ' logo'" (error)="failed = true" />
      } @else {
      <span aria-hidden="true">{{fallback}}</span>
      }
    </span>
  `,
  styles: [
    `:host { display: inline-block; height: 33px; width: 33px; flex-shrink: 0; }
     .stock-icon { width: 100%; height: 100%; overflow: hidden; }
     img { width: 100%; height: 100%; object-fit: contain; padding: 4px; }`,
  ],
})
export class CompanyLogoComponent implements OnChanges {
  @Input() symbol = "";
  @Input() name = "";
  @Input() color = "var(--purple)";
  failed = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes["symbol"] || changes["name"]) this.failed = false;
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
    return `https://img.logo.dev/${lookupPath}?token=${encodeURIComponent(token)}&size=64&format=png`;
  }
}

declare global {
  interface Window {
    LOGO_DEV_TOKEN?: string;
  }
}
