import { HttpErrorResponse } from "@angular/common/http";
import { ThemeToggleComponent } from "../../components/branding/theme-toggle/theme-toggle";
import { BrandLogoComponent } from "../../components/branding/brand-logo/brand-logo";
import { finalize } from "rxjs";
import { ChangeDetectionStrategy } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Component, inject, DestroyRef, signal } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { WorkspaceService } from "../../services/workspace.service";

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [":host { display: block; }"],
  selector: "app-login-page",
  imports: [
    ThemeToggleComponent,
    BrandLogoComponent,
    CommonModule,
    FormsModule,
  ],
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

  get passwordError(): string {
    const pwd = this.password;
    if (!pwd) return "";
    if (pwd.length < 8) {
      return `Password must be at least 8 characters long (currently ${pwd.length}).`;
    }
    return "";
  }

  get isPasswordValid(): boolean {
    return !!this.password && this.password.length >= 8;
  }

  get isFormValid(): boolean {
    if (!this.email.trim()) return false;
    if (!this.isPasswordValid) return false;
    if (this.signup && !this.fullName.trim()) return false;
    return true;
  }

  protected authenticate() {
    if (this.workspace.busy) return;
    this.workspace.error = "";

    if (this.signup && !this.fullName.trim()) {
      this.workspace.error = "Please enter your full name.";
      return;
    }
    if (!this.email.trim()) {
      this.workspace.error = "Please enter your email address.";
      return;
    }
    if (!this.password) {
      this.workspace.error = "Please enter your password.";
      return;
    }
    if (this.password.length < 8) {
      this.workspace.error = "Password must be at least 8 characters long.";
      return;
    }

    this.workspace.busy = true;
    const isSignup = this.signup;
    const request = isSignup
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
          this.workspace.clearAccountState();
          this.workspace.userId = r.user.id;
          this.workspace.profileName = r.user.fullName;
          this.workspace.profileEmail = r.user.email;
          this.workspace.go("Profile");
          this.workspace.discoverAccounts();
          this.workspace.notice =
            "Signed in. Your brokerage accounts will appear below.";
        },
        error: (err: HttpErrorResponse) => {
          this.workspace.busy = false;
          this.password = "";
          const serverMessage =
            typeof err.error?.message === "string"
              ? err.error.message
              : typeof err.error?.detail === "string"
                ? err.error.detail
                : Array.isArray(err.error?.errors) && err.error.errors[0]?.defaultMessage
                  ? err.error.errors[0].defaultMessage
                  : "";
          if (err.status === 0) {
            this.workspace.error =
              "Cannot reach the Java backend. Check that Spring Boot is running and the Angular proxy target is correct.";
          } else if (err.status === 409) {
            this.workspace.error = "An account with this email already exists.";
          } else if (err.status === 400 || err.status === 422) {
            this.workspace.error =
              serverMessage ||
              "Password must be at least 8 characters long.";
          } else if (err.status === 401) {
            this.workspace.error = "Incorrect email or password.";
          } else if (err.status === 404) {
            this.workspace.error =
              "Authentication endpoint not found. Check the Angular proxy and Java server address.";
          } else if (
            err.status === 502 ||
            err.status === 503 ||
            err.status === 504
          ) {
            this.workspace.error =
              "The Java backend is unavailable. Check the backend server and proxy.";
          } else if (err.status >= 500) {
            this.workspace.error = `Server error (${err.status}). Check the Spring Boot logs for the database or authentication failure.`;
          } else {
            this.workspace.error =
              serverMessage ||
              `${isSignup ? "Registration" : "Sign-in"} failed (HTTP ${err.status}).`;
          }
        },
      });
  }
}
