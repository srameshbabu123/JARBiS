import { CashTransferComponent } from "../../components/funding/cash-transfer/cash-transfer";
import { ChangeDetectionStrategy } from "@angular/core";
import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { PortfolioSummaryComponent } from "../../components/portfolio/portfolio-summary/portfolio-summary";
import { HoldingsTableComponent } from "../../components/portfolio/holdings-table/holdings-table";
import { ProfileDetailsComponent } from "../../components/profile/profile-details/profile-details";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-profile-page",
  imports: [
    CashTransferComponent,
    CommonModule,
    FormsModule,
    PortfolioSummaryComponent,
    HoldingsTableComponent,
    ProfileDetailsComponent,
  ],
  templateUrl: "./profile-page.html",
})
export class ProfilePageComponent {}
