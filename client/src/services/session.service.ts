import { Injectable, signal } from "@angular/core";
import { AuthResponse } from "../models/brokerage.models";

const SESSION_STORAGE_KEY = "velocity-session";

@Injectable({ providedIn: "root" })
export class SessionService {
  private readonly session = signal<AuthResponse | null>(this.restoreSession());

  get currentUser() {
    return this.session()?.user ?? null;
  }

  get token() {
    return this.session()?.token ?? "";
  }

  remember(result: AuthResponse) {
    this.session.set(result);
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(result));
    } catch {
      /* ignore storage errors */
    }
  }

  signOut() {
    this.session.set(null);
    try {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      /* ignore storage errors */
    }
  }

  private restoreSession(): AuthResponse | null {
    try {
      const saved = localStorage.getItem(SESSION_STORAGE_KEY);
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  }
}
