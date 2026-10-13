import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { PortfolioSummaryComponent } from "../../portfolio/portfolio-summary";
import { HoldingsTableComponent } from "../../portfolio/holdings-table";
import { ProfileDetailsComponent } from "../../profile/profile-details";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-profile-page",
  imports: [
    CommonModule,
    FormsModule,
    PortfolioSummaryComponent,
    HoldingsTableComponent,
    ProfileDetailsComponent,
  ],
  templateUrl: "./profile-page.html",
})
export class ProfilePageComponent {}
