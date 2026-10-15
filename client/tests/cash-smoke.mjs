import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { chromium } from 'playwright';
const root=new URL('../dist/client/browser/',import.meta.url).pathname;
const server=createServer(async(req,res)=>{
 try{const file=join(root,req.url==='/'?'index.html':req.url.split('?')[0]);res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html','.jpg':'image/jpeg'})[extname(file)]||'application/octet-stream');res.end(await readFile(file))}catch{res.writeHead(404).end()}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--no-zygote','--single-process','--disable-gpu']});
try{
 const page=await browser.newPage({viewport:{width:1440,height:1100}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const origin=`http://127.0.0.1:${server.address().port}`;
 await page.goto(origin+'/#/profile');
 const panel=page.locator('.cash-panel');const amount=panel.getByRole('spinbutton',{name:'Cash amount'});
 await panel.getByRole('heading',{name:'Manage cash'}).waitFor();
 await amount.fill('0');assert(await panel.getByRole('button',{name:'Review deposit'}).isDisabled());
 await amount.fill('1.001');assert(await panel.getByRole('button',{name:'Review deposit'}).isDisabled());
 await amount.fill('25.25');await panel.getByRole('button',{name:'Review deposit'}).click();await panel.getByRole('button',{name:'Confirm demo cash change'}).click();
 await panel.getByRole('status').waitFor();assert.match(await panel.innerText(),/\$12,475.25/);
 await panel.getByRole('button',{name:'Withdraw',exact:true}).click();await amount.fill('100000');assert(await panel.getByRole('button',{name:'Review withdrawal'}).isDisabled());
 await amount.fill('25.25');await panel.getByRole('button',{name:'Review withdrawal'}).click();await panel.getByRole('button',{name:'Confirm demo cash change'}).click();await panel.getByRole('status').waitFor();assert.match(await panel.innerText(),/\$12,450.00/);
 let cashPosts=0;let ambiguous=false;let multiple=false;
 const accounts=[{id:42,ownerId:9,balance:10000,accountType:'INVESTMENT',currency:'USD'},{id:77,ownerId:9,balance:500,accountType:'SAVINGS',currency:'EUR'}];
 await page.route('**/api/**',async route=>{
  const request=route.request();const path=new URL(request.url()).pathname;let json;
  if(path==='/api/auth/login')json={user:{id:9,fullName:'Test User',email:'test@example.com'},token:'cash-test-session',tokenType:'Bearer'};
  else{
   assert.equal(request.headers().authorization,'Bearer cash-test-session');
   if(path==='/api/accounts/mine')json=multiple?accounts:[accounts[0]];
   else if(path==='/api/users/9')json={id:9,fullName:'Test User',email:'test@example.com'};
   else if(/^\/api\/accounts\/mine\/(42|77)$/.test(path))json=accounts.find(a=>a.id===Number(path.split('/').at(-1)));
   else if(path.startsWith('/api/holdings/account/'))json=[];
   else if(path.startsWith('/api/orders/account/'))json=[];
   else if(/^\/api\/accounts\/(42|77)\/(deposit|withdraw)$/.test(path)){
    cashPosts++;const body=request.postDataJSON();assert.deepEqual(Object.keys(body),['amount']);assert.equal(request.method(),'POST');
    const account=accounts.find(a=>a.id===Number(path.split('/')[3]));
    account.balance+=path.endsWith('/deposit')?body.amount:-body.amount;
    await new Promise(resolve=>setTimeout(resolve,150));
    if(ambiguous){await route.fulfill({status:503,json:{message:'Unavailable'}});return}
    json=account;
   }else throw Error('Unexpected request '+path);
  }
  await route.fulfill({json});
 });
 await page.getByRole('button',{name:'Sign in ↗',exact:true}).click();await page.getByRole('heading',{name:'Welcome back.',exact:true}).waitFor();await page.getByLabel('Email address').fill('test@example.com');await page.getByLabel('Password',{exact:true}).fill('test-password');await page.getByRole('button',{name:'Sign in →',exact:true}).click();
 await page.getByText('Loaded account 42 · USD').waitFor();assert.equal(await page.getByLabel('User ID',{exact:true}).count(),0);assert.equal(await page.getByLabel('Account ID',{exact:true}).count(),0);
 await amount.fill('50.25');await panel.getByRole('button',{name:'Review deposit'}).click();await panel.getByRole('button',{name:'Confirm cash change',exact:true}).dblclick();await panel.getByRole('status').waitFor();assert.equal(cashPosts,1);assert.match(await panel.innerText(),/\$10,050.25/);
 await panel.getByRole('button',{name:'Withdraw',exact:true}).click();await amount.fill('20');await panel.getByRole('button',{name:'Review withdrawal'}).click();await panel.getByRole('button',{name:'Confirm cash change',exact:true}).click();await panel.getByRole('status').waitFor();assert.equal(cashPosts,2);assert.match(await panel.innerText(),/\$10,030.25/);
 multiple=true;await page.getByRole('button',{name:'Refresh accounts',exact:true}).click();await page.getByRole('option',{name:/Savings/}).waitFor({state:'attached'});await page.getByLabel('Brokerage account',{exact:true}).selectOption({index:2});await page.getByText('Loaded account 77 · EUR').waitFor();
 await panel.getByRole('button',{name:'Deposit',exact:true}).click();await amount.fill('10');await panel.getByRole('button',{name:'Review deposit'}).click();await panel.getByText(/Deposit €10.00/).waitFor();
 ambiguous=true;await panel.getByRole('button',{name:'Confirm cash change',exact:true}).click();await panel.getByRole('alert').waitFor();assert(await panel.getByRole('button',{name:'Confirm cash change',exact:true}).isDisabled());assert.equal(cashPosts,3);
 await panel.getByRole('button',{name:'Refresh cash balance',exact:true}).click();await page.getByText('Loaded account 77 · EUR').waitFor();await page.waitForFunction(()=>document.querySelector('.cash-panel').innerText.includes('€510.00'));
 await mkdir(new URL('../test-results/',import.meta.url),{recursive:true});await page.screenshot({path:new URL('../test-results/cash.png',import.meta.url).pathname,fullPage:true});
 await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:new URL('../test-results/cash-mobile.png',import.meta.url).pathname,fullPage:true});
 assert.deepEqual(errors,[]);console.log('PASS: demo/live deposits and withdrawals, amount validation, automatic session-based account discovery, multiple accounts, currency, duplicate prevention, ambiguous failure, balance refresh, mobile layout.');
}finally{await browser.close();server.close()}
