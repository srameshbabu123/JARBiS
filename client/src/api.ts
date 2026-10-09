import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { tap } from 'rxjs';
export interface User { id:number; fullName:string; email:string }
export interface AuthResponse { user:User; token:string; tokenType:string }
export interface Account { id:number; accountType:string; currency:string; balance:number; ownerId:number }
export interface Holding { id:number; accountId:number; assetId:number; assetName:string; quantity:number; averagePrice:number; marketValue:number; costBasis:number; unrealizedPnL:number; unrealizedPnLPercentage:number; isProfitable:boolean }
export interface Order { id:number; asset:{id?:number;name:string;symbol?:string}|null; side:string; quantity:number; remainingQuantity?:number; price:number; status:string }
export interface CreateOrder { accountId:number; assetId:number; side:'BUY'|'SELL'; quantity:number; price:number }
@Injectable({providedIn:'root'})
export class BrokerageApi {
 private http=inject(HttpClient);
 // Session credentials stay in memory and are cleared by refresh/sign-out.
 private token='';
 currentUser:User|null=null;
 private get options(){return {headers:new HttpHeaders(this.token?{Authorization:`Bearer ${this.token}`}:{})}}
 private remember(result:AuthResponse){this.token=result.token;this.currentUser=result.user}
 login(body:{email:string;password:string}){return this.http.post<AuthResponse>('/api/auth/login',body).pipe(tap(r=>this.remember(r)))}
 createUser(body:{fullName:string;email:string;password:string}) {return this.http.post<AuthResponse>('/api/auth/register',body).pipe(tap(r=>this.remember(r)))}
 signOut(){this.token='';this.currentUser=null}
 user(id:number){return this.http.get<User>(`/api/users/${id}`,this.options)}
 balance(id:number){return this.http.get<number>(`/api/users/${id}/balance`,this.options)}
 account(id:number){return this.http.get<Account>(`/api/accounts/${id}`,this.options)}
 holdings(id:number){return this.http.get<Holding[]>(`/api/holdings/account/${id}`,this.options)}
 orders(id:number){return this.http.get<Order[]>(`/api/orders/account/${id}`,this.options)}
 updateUser(id:number,body:{fullName:string;email:string}){return this.http.put<User>(`/api/users/${id}`,body,this.options)}
 createOrder(body:CreateOrder){return this.http.post<Order>('/api/orders',body,this.options)}
}
