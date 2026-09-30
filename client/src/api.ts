import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
export interface User { id:number; fullName:string; email:string }
export interface Account { id:number; accountType:string; currency:string; balance:number; ownerId:number }
export interface Holding { id:number; accountId:number; assetId:number; assetName:string; quantity:number; averagePrice:number; marketValue:number; costBasis:number; unrealizedPnL:number; unrealizedPnLPercentage:number; isProfitable:boolean }
// OrderController returns entities, not OrderResponseDto.
export interface Order { id:number; asset:{name:string; symbol?:string}|null; side:string; quantity:number; price:number; status:string }
@Injectable({providedIn:'root'})
export class BrokerageApi {
 private http=inject(HttpClient);
 createUser(body:{fullName:string;email:string;password:string}) {return this.http.post<User>('/api/users',body)}
 user(id:number){return this.http.get<User>(`/api/users/${id}`)}
 balance(id:number){return this.http.get<number>(`/api/users/${id}/balance`)}
 account(id:number){return this.http.get<Account>(`/api/accounts/${id}`)}
 holdings(id:number){return this.http.get<Holding[]>(`/api/holdings/account/${id}`)}
 orders(id:number){return this.http.get<Order[]>(`/api/orders/account/${id}`)}
 updateUser(id:number,body:{fullName:string;email:string}){return this.http.put<User>(`/api/users/${id}`,body)}
}
