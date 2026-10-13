import { Injectable, signal } from "@angular/core";
import { AuthResponse } from "../models/brokerage.models";
@Injectable({ providedIn: "root" })
export class SessionService {
  private readonly session = signal<AuthResponse | null>(null);
  get currentUser() {
    return this.session()?.user ?? null;
  }
  get token() {
    return this.session()?.token ?? "";
  }
  remember(result: AuthResponse) {
    this.session.set(result);
  }
  signOut() {
    this.session.set(null);
  }
}
