import { Component, EventEmitter, Input, Output, inject, OnChanges, SimpleChanges, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Account, BrokerageApi, CreateOrder, Holding, Order } from './api';

@Component({selector:'app-trade',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./trade.html',styleUrl:'./trade.css'})
export class TradeComponent implements OnChanges {
 private destroyRef=inject(DestroyRef);
 @Input() account:Account|null=null;
 @Input() cash=0;
 @Input() holdings:Holding[]=[];
 @Input() orders:Order[]=[];
 @Output() submitted=new EventEmitter<Order>();
 @Output() navigate=new EventEmitter<string>();
 api=inject(BrokerageApi);
 side:'BUY'|'SELL'='BUY';
 assetId:number|null=1;
 quantity:number|null=1;
 price:number|null=142.87;
 reviewing:CreateOrder|null=null;
 submitting=false;
 error='';
 receipt:Order|null=null;
 uncertain=false;
 ngOnChanges(changes:SimpleChanges){
  if(changes['account']||changes['holdings']){this.chooseAsset(this.holdings[0]?.assetId??null)}
 }
 get connected(){return this.account!==null && this.api.currentUser!==null}
 get currency(){return this.account?.currency||'USD'}
 get holding(){return this.holdings.find(h=>h.assetId===this.assetId)}
 get assetLabel(){return this.holding?.assetName||(this.assetId?'Asset #'+this.assetId:'Select an asset')}
 get total(){return (this.quantity||0)*(this.price||0)}
 get pending(){return this.orders.filter(o=>o.status==='PENDING'||o.status==='PARTIALLY_FILLED')}
 get availableCash(){return Math.max(0,this.cash-this.pending.filter(o=>o.side==='BUY').reduce((n,o)=>n+(o.remainingQuantity??o.quantity)*o.price,0))}
 get availableShares(){return Math.max(0,(this.holding?.quantity||0)-this.pending.filter(o=>o.side==='SELL'&&o.asset?.id===this.assetId).reduce((n,o)=>n+(o.remainingQuantity??o.quantity),0))}
 get validation(){
  if(!Number.isSafeInteger(this.assetId)||Number(this.assetId)<=0)return 'Choose a valid asset ID.';
  if(!Number.isFinite(this.quantity)||Number(this.quantity)<=0)return 'Enter a quantity greater than zero.';
  if(!Number.isFinite(this.price)||Number(this.price)<=0)return 'Enter a price greater than zero.';
  if(!Number.isFinite(this.total))return 'The order total is too large.';
  if(this.side==='BUY'&&this.total>this.availableCash)return 'This order exceeds your estimated available cash.';
  if(this.side==='SELL'&&Number(this.quantity)>this.availableShares)return 'This order exceeds your available holdings.';
  return '';
 }
 edit(){this.reviewing=null;this.receipt=null;this.error='';this.uncertain=false}
 chooseAsset(id:number|null){this.assetId=id;this.edit();const h=this.holding;this.price=h&&h.quantity>0?Math.round(h.marketValue/h.quantity*100)/100:null}
 review(){this.error=this.validation;if(this.error)return;this.receipt=null;this.reviewing={accountId:this.account?.id||1,assetId:this.assetId!,side:this.side,quantity:this.quantity!,price:this.price!}}
 confirm(){
  if(!this.reviewing||this.submitting||this.uncertain)return;
  if(this.validation){this.error=this.validation;this.reviewing=null;return}
  const request={...this.reviewing};
  this.submitting=true;this.error='';
  if(!this.connected){
   const order:Order={id:Math.max(1048,...this.orders.map(o=>o.id))+1,asset:{id:request.assetId,name:this.assetLabel},side:request.side,quantity:request.quantity,remainingQuantity:request.quantity,price:request.price,status:'PENDING'};
   this.success(order);return;
  }
  if(this.account!.ownerId!==this.api.currentUser!.id){this.submitting=false;this.error='Load an account belonging to the signed-in user.';return}
  this.api.createOrder(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:order=>this.success(order),error:(e:HttpErrorResponse)=>{
   this.submitting=false;
   if(e.status===0||e.status>=500){this.uncertain=true;this.error='We could not confirm whether the order was accepted. Check order history before placing it again.'}
   else if(e.status===401||e.status===403){this.error='Your session cannot place this order. Sign in again and reload your account.'}
   else {this.error=typeof e.error?.message==='string'?e.error.message:'The order was rejected. Check the asset, account, quantity, and price.'}
  }});
 }
 private success(order:Order){this.submitting=false;this.reviewing=null;this.receipt=order;this.submitted.emit(order)}
}
