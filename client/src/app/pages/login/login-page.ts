import { finalize } from "rxjs";
import { ChangeDetectionStrategy } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Component, inject, DestroyRef, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../workspace/workspace.service";
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-login-page",
  imports: [CommonModule, FormsModule],
  templateUrl: "./login-page.html",
})
export class LoginPageComponent {
  protected readonly workspace = inject(WorkspaceService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly signupState = signal<boolean>(false);
  get signup() {
    return this.signupState();
  }
  set signup(value: boolean) {
    this.signupState.set(value);
  }
  private readonly emailState = signal<string>("");
  get email() {
    return this.emailState();
  }
  set email(value: string) {
    this.emailState.set(value);
  }
  private readonly passwordState = signal<string>("");
  get password() {
    return this.passwordState();
  }
  set password(value: string) {
    this.passwordState.set(value);
  }
  private readonly fullNameState = signal<string>("");
  get fullName() {
    return this.fullNameState();
  }
  set fullName(value: string) {
    this.fullNameState.set(value);
  }
  protected authenticate() {
    if (this.workspace.busy) return;
    this.workspace.error = "";
    if (
      !this.email.trim() ||
      !this.password ||
      (this.signup && (!this.fullName.trim() || this.password.length < 8))
    ) {
      this.workspace.error =
        "Complete all required fields with a valid email and password.";
      return;
    }
    this.workspace.busy = true;
    const request = this.signup
      ? this.workspace.api.createUser({
          fullName: this.fullName.trim(),
          email: this.email.trim(),
          password: this.password,
        })
      : this.workspace.api.login({
          email: this.email.trim(),
          password: this.password,
        });
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => (this.workspace.busy = false)),
      )
      .subscribe({
        next: (r) => {
          this.workspace.busy = false;
          this.password = "";
          this.workspace.resetDemo();
          this.workspace.userId = r.user.id;
          this.workspace.profileName = r.user.fullName;
          this.workspace.profileEmail = r.user.email;
          this.workspace.go("Profile");
          this.workspace.notice =
            "Signed in. Enter your account ID and load the account to trade.";
        },
        error: () => {
          this.workspace.busy = false;
          this.password = "";
          this.workspace.error =
            "Sign-in or registration failed. Check your credentials and that the current development API is running.";
        },
      });
  }
}
