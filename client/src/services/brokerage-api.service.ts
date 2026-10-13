import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { tap } from "rxjs";
import { SessionService } from "./session.service";
import {
  User,
  AuthResponse,
  Account,
  Holding,
  Order,
  CreateOrder,
} from "../models/brokerage.models";
@Injectable({ providedIn: "root" })
export class BrokerageApiService {
  private http = inject(HttpClient);
  private session = inject(SessionService);
  login(body: { email: string; password: string }) {
    return this.http
      .post<AuthResponse>("/api/auth/login", body)
      .pipe(tap((r) => this.session.remember(r)));
  }
  createUser(body: { fullName: string; email: string; password: string }) {
    return this.http
      .post<AuthResponse>("/api/auth/register", body)
      .pipe(tap((r) => this.session.remember(r)));
  }
  user(id: number) {
    return this.http.get<User>(`/api/users/${id}`);
  }
  balance(id: number) {
    return this.http.get<number>(`/api/users/${id}/balance`);
  }
  account(id: number) {
    return this.http.get<Account>(`/api/accounts/mine/${id}`);
  }
  myAccounts() {
    return this.http.get<Account[]>("/api/accounts/mine");
  }
  changeCash(id: number, amount: number, action: "deposit" | "withdraw") {
    return this.http.post<Account>(`/api/accounts/${id}/${action}`, { amount });
  }
  holdings(id: number) {
    return this.http.get<Holding[]>(`/api/holdings/account/${id}`);
  }
  orders(id: number) {
    return this.http.get<Order[]>(`/api/orders/account/${id}`);
  }
  updateUser(id: number, body: { fullName: string; email: string }) {
    return this.http.put<User>(`/api/users/${id}`, body);
  }
  createOrder(body: CreateOrder) {
    return this.http.post<Order>("/api/orders", body);
  }
}
