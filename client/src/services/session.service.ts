import { Injectable, signal } from "@angular/core";
import { AuthResponse } from "../models/brokerage.models";

const SESSION_STORAGE_KEY = "velocity-session";

function tokenExpiresAt(token: string): number {
  try {
    const payload = token.split(".")[1];
    if (!payload) return 0;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(base64));
    return typeof claims.exp === "number" ? claims.exp * 1000 : 0;
  } catch {
    return 0;
  }
}

@Injectable({ providedIn: "root" })
export class SessionService {
  private readonly session = signal<AuthResponse | null>(this.restoreSession());

  get currentUser() {
    return this.token ? this.session()?.user ?? null : null;
  }

  get token() {
    const current = this.session();
    if (!current) return "";
    if (tokenExpiresAt(current.token) <= Date.now()) {
      this.signOut();
      return "";
    }
    return current.token;
  }

  remember(result: AuthResponse) {
    if (!result?.token || !result?.user?.id || tokenExpiresAt(result.token) <= Date.now()) {
      throw new Error("The server did not return a valid session.");
    }
    this.session.set(result);
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(result));
    } catch {
      // The session stays usable in memory when browser storage is disabled.
    }
  }

  signOut() {
    this.session.set(null);
    try {
      localStorage.removeItem(SESSION_STORAGE_KEY);
      localStorage.removeItem("velocity-account-id");
    } catch {
      // Ignore browser storage errors.
    }
  }

  private restoreSession(): AuthResponse | null {
    try {
      const saved = localStorage.getItem(SESSION_STORAGE_KEY);
      if (!saved) return null;
      const result = JSON.parse(saved) as AuthResponse;
      if (!result?.user?.id || !result?.token || tokenExpiresAt(result.token) <= Date.now()) {
        localStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }
      return result;
    } catch {
      return null;
    }
  }
}
