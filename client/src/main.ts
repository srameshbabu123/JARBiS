import 'zone.js';
import { Component, inject } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { BrokerageApi, Holding, Order, Account } from './api';
import { TradeComponent } from './trade';
const STOCKS=[
 {symbol:'NVDA',name:'NVIDIA',price:142.87,change:3.42,sector:'Technology',cap:3490,color:'#ad83fa'},
 {symbol:'AAPL',name:'Apple',price:228.26,change:1.18,sector:'Technology',cap:3420,color:'#e7e7ef'},
 {symbol:'MSFT',name:'Microsoft',price:428.76,change:0.86,sector:'Technology',cap:3190,color:'#fa9e64'},
 {symbol:'TSLA',name:'Tesla',price:248.50,change:-1.24,sector:'Automotive',cap:798,color:'#ed81b4'},
 {symbol:'AMZN',name:'Amazon',price:198.12,change:2.13,sector:'Consumer',cap:2080,color:'#ffac69'},
 {symbol:'JPM',name:'JPMorgan Chase',price:223.45,change:-0.42,sector:'Financials',cap:628,color:'#9b8bf1'},
 {symbol:'GOOGL',name:'Alphabet',price:176.80,change:1.67,sector:'Technology',cap:2170,color:'#e680b6'}];
@Component({selector:'app-root',standalone:true,imports:[CommonModule,FormsModule,TradeComponent],templateUrl:'./app.html'})
export class App {
 api=inject(BrokerageApi); page=location.hash.slice(1)||'overview'; nav=['Overview','Trade','Charts','Watchlist','Screener','News','Order history','Profile']; icons=['◈','⇄','⌁','☆','⊞','▤','↔','◉'];
 stocks=STOCKS; selected=STOCKS[0]; query=''; sector='All sectors'; gainers=false; range='1M'; orderFilter='All'; signup=false; email=''; password=''; fullName=''; error=''; notice=''; busy=false; live=false; userId=1; accountId=1; profileName='Alex Morgan'; profileEmail='alex@example.com'; cash=12450; holdings:Holding[]=[]; orders:Order[]=[];
 loadedAccount:Account|null=null;
 watched:string[]=this.loadWatchlist();
 constructor(){window.addEventListener('hashchange',()=>this.page=location.hash.slice(1)||'overview');this.resetDemo()}
 loadWatchlist(){try{const v=JSON.parse(localStorage.getItem('jarbis-watchlist')||'["NVDA","AAPL","TSLA","MSFT"]');return Array.isArray(v)?v.filter(x=>typeof x==='string'):[]}catch{return ['NVDA','AAPL','TSLA','MSFT']}}
 go(p:string){this.page=p.toLowerCase().replace(' ','-');location.hash=this.page;this.notice='';this.error=''}
 resetDemo(){this.loadedAccount=null;this.live=false;this.profileName='Alex Morgan';this.profileEmail='alex@example.com';this.userId=1;this.accountId=1;this.cash=12450;this.holdings=STOCKS.slice(0,4).map((s,i)=>{const q=[80,45,30,25][i],a=s.price*.86;return {id:i+1,accountId:1,assetId:i+1,assetName:s.name,quantity:q,averagePrice:a,marketValue:q*s.price,costBasis:q*a,unrealizedPnL:q*(s.price-a),unrealizedPnLPercentage:16.28,isProfitable:true}});this.orders=STOCKS.slice(0,5).map((s,i)=>({id:1048-i,asset:{id:i+1,name:s.name,symbol:s.symbol},side:i===3?'SELL':'BUY',quantity:[10,5,12,8,15][i],price:s.price,status:i===0?'PENDING':i===4?'CANCELLED':'COMPLETED'}));}
 get invested(){return this.holdings.reduce((a,h)=>a+h.marketValue,0)} get pnl(){return this.holdings.reduce((a,h)=>a+h.unrealizedPnL,0)}
 get filtered(){return this.stocks.filter(s=>(s.name+' '+s.symbol).toLowerCase().includes(this.query.toLowerCase())&&(this.sector==='All sectors'||s.sector===this.sector)&&(!this.gainers||s.change>0))}
 get history(){return this.orders.filter(o=>this.orderFilter==='All'||o.status===this.orderFilter)}
 toggle(s:string){this.watched=this.watched.includes(s)?this.watched.filter(x=>x!==s):[...this.watched,s];localStorage.setItem('jarbis-watchlist',JSON.stringify(this.watched))}
 chart(s:typeof STOCKS[number]){this.selected=s;this.go('Charts')}
 get path(){const r=['1D','1W','1M','3M','1Y','ALL'].indexOf(this.range);return Array.from({length:80},(_,i)=>`${i===0?'M':'L'} ${i*10} ${205-i*1.65+Math.sin(i*1.7+r)*13+Math.cos(i*.53+r)*20}`).join(' ')}
 authenticate(){
  if(this.busy)return;
  this.error='';
  if(!this.email.trim()||!this.password||(this.signup&&(!this.fullName.trim()||this.password.length<8))){this.error='Complete all required fields with a valid email and password.';return}
  this.busy=true;
  const request=this.signup?this.api.createUser({fullName:this.fullName.trim(),email:this.email.trim(),password:this.password}):this.api.login({email:this.email.trim(),password:this.password});
  request.subscribe({next:r=>{this.busy=false;this.password='';this.resetDemo();this.userId=r.user.id;this.profileName=r.user.fullName;this.profileEmail=r.user.email;this.go('Profile');this.notice='Signed in. Enter your account ID and load the account to trade.'},error:()=>{this.busy=false;this.password='';this.error='Sign-in or registration failed. Check your credentials and that the current development API is running.'}});
 }
 signOut(){this.api.signOut();this.resetDemo();this.go('Login')}
 orderSubmitted(order:Order){this.orders=[order,...this.orders.filter(o=>o.id!==order.id)]}
 refreshOrders(){if(!this.loadedAccount)return;this.api.orders(this.loadedAccount.id).subscribe({next:orders=>{this.orders=orders;this.notice='Order status refreshed.'},error:()=>this.error='Could not refresh order history. Please try again.'})}
 connect(){if(this.busy)return;this.error='';if(!this.api.currentUser){this.error='Sign in before loading an account.';return}this.userId=this.api.currentUser.id;if(!Number.isInteger(this.userId)||!Number.isInteger(this.accountId)||this.userId<1||this.accountId<1){this.error='Enter valid positive user and account IDs.';return}this.busy=true;forkJoin({user:this.api.user(this.userId),account:this.api.account(this.accountId),holdings:this.api.holdings(this.accountId),orders:this.api.orders(this.accountId)}).subscribe({next:r=>{this.busy=false;if(r.account.ownerId!==r.user.id){this.error='This account does not belong to the selected user.';return}this.loadedAccount=r.account;this.live=true;this.profileName=r.user.fullName;this.profileEmail=r.user.email;this.cash=r.account.balance;this.holdings=r.holdings;this.orders=r.orders;this.notice='Loaded account '+r.account.id+' · '+r.account.currency},error:()=>{this.busy=false;this.error='Unable to load account. Check API connection and IDs. Demo data remains visible.'}})}
 saveProfile(){if(!this.live){this.notice='Demo profile updated for this preview.';return}this.api.updateUser(this.userId,{fullName:this.profileName,email:this.profileEmail}).subscribe({next:()=>this.notice='Profile saved.',error:()=>this.error='Profile could not be saved.'})}
}
bootstrapApplication(App,{providers:[provideHttpClient()]}).catch(console.error);
