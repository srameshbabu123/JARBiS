import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { chromium } from 'playwright';

// Serve the production build; all /api requests below are intercepted in the browser.
const root=new URL('../dist/client/browser/',import.meta.url).pathname;
const server=createServer(async(req,res)=>{
 try {const file=join(root,req.url==='/'?'index.html':req.url.split('?')[0]);res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'application/octet-stream');res.end(await readFile(file))}
 catch {res.writeHead(404).end()}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--no-zygote','--single-process','--disable-gpu']});
try {
 const page=await browser.newPage({viewport:{width:1440,height:1100}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const origin=`http://127.0.0.1:${server.address().port}`;
 await page.goto(origin+'/#trade');
 await page.getByRole('heading',{name:'Order ticket'}).waitFor();
 await page.screenshot({path:new URL('../preview/trade.png',import.meta.url).pathname,fullPage:true});
 const quantity=page.getByLabel('Quantity',{exact:true});
 await quantity.fill('0');assert(await page.getByRole('button',{name:'Review buy order'}).isDisabled());
 await quantity.fill('10000');assert(await page.getByRole('button',{name:'Review buy order'}).isDisabled());
 await quantity.fill('2.5');await page.getByRole('button',{name:'Review buy order'}).click();
 assert(await quantity.isDisabled());
 await page.getByRole('button',{name:'Place demo order'}).click();
 assert.match(await page.locator('.receipt').innerText(),/PENDING/);
 await page.getByRole('button',{name:'New order',exact:true}).click();
 await page.getByRole('button',{name:'↘ Sell',exact:true}).click();
 await quantity.fill('81');assert(await page.getByRole('button',{name:'Review sell order'}).isDisabled());
 await quantity.fill('5');await page.getByRole('button',{name:'Review sell order'}).click();await page.getByRole('button',{name:'Place demo order'}).click();
 assert.match(await page.locator('.account-panel').innerText(),/75 units/);
 await page.getByRole('button',{name:'View order history ↗'}).click();
 assert.match(await page.locator('tbody tr').first().innerText(),/SELL/);
 // Authenticated flow uses fixtures only. Verify exact request contract and no duplicate POST.
 let posted=0;let failMode=false;let postedBody;
 const holding={id:1,accountId:42,assetId:77,assetName:'Test Asset',quantity:10,averagePrice:80,marketValue:1000,costBasis:800,unrealizedPnL:200,unrealizedPnLPercentage:25,isProfitable:true};
 await page.route('**/api/**',async route=>{
  const req=route.request();const path=new URL(req.url()).pathname;
  let body;
  if(path==='/api/auth/login'){body={user:{id:9,fullName:'Test User',email:'test@example.com'},token:'test-session-only',tokenType:'Bearer'}}
  else {
   assert.equal(req.headers().authorization,'Bearer test-session-only');
   if(path==='/api/users/9')body={id:9,fullName:'Test User',email:'test@example.com'};
   else if(path==='/api/accounts/42')body={id:42,ownerId:9,balance:10000,accountType:'BROKERAGE',currency:'USD'};
   else if(path==='/api/holdings/account/42')body=[holding];
   else if(path==='/api/orders/account/42')body=[];
   else if(path==='/api/orders'&&req.method()==='POST'){
    posted++;postedBody=req.postDataJSON();
    await new Promise(resolve=>setTimeout(resolve,150));
    if(failMode){await route.fulfill({status:503,json:{message:'test unavailable'}});return}
    body={id:700,asset:{id:77,name:'Test Asset'},side:postedBody.side,quantity:postedBody.quantity,price:postedBody.price,remainingQuantity:postedBody.quantity,status:'PENDING'};
   } else throw Error('Unexpected request '+path);
  }
  await route.fulfill({json:body});
 });
 await page.getByRole('button',{name:'Sign in ↗',exact:true}).click();
 await page.getByLabel('Email address').fill('test@example.com');await page.getByLabel('Password',{exact:true}).fill('test-password');await page.getByRole('button',{name:'Sign in →',exact:true}).click();
 await page.getByLabel('Account ID',{exact:true}).fill('42');await page.getByRole('button',{name:'Load account',exact:true}).click();await page.getByText('Loaded account 42 · USD').waitFor();
 await page.locator('nav button').filter({hasText:'Trade'}).click();
 await page.getByLabel('Asset from your holdings').selectOption({label:'Test Asset · #77'});
 await page.getByLabel('Quantity',{exact:true}).fill('2');
 await page.getByRole('button',{name:'Review buy order'}).click();
 await page.getByRole('button',{name:'Submit order',exact:true}).dblclick();
 await page.locator('.receipt').waitFor();
 assert.equal(posted,1);assert.deepEqual(postedBody,{accountId:42,assetId:77,side:'BUY',quantity:2,price:100});
 await page.getByRole('button',{name:'New order',exact:true}).click();
 failMode=true;
 await page.getByRole('button',{name:'↘ Sell',exact:true}).click();await page.getByLabel('Quantity',{exact:true}).fill('1');
 await page.getByRole('button',{name:'Review sell order'}).click();await page.getByRole('button',{name:'Submit order',exact:true}).click();
 await page.getByRole('alert').waitFor();assert(await page.getByRole('button',{name:'Submit order',exact:true}).isDisabled());
 assert.match(await page.getByRole('alert').innerText(),/could not confirm/);assert.equal(posted,2);assert.equal(postedBody.side,'SELL');
 await page.getByRole('button',{name:'Sign out',exact:true}).click();
 await page.getByRole('button',{name:'Explore demo ↗'}).click();await page.locator('nav button').filter({hasText:'Trade'}).click();
 assert.match(await page.locator('.mode').innerText(),/Demo/);
 await page.setViewportSize({width:390,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:new URL('../preview/trade-mobile.png',import.meta.url).pathname,fullPage:true});
 assert.deepEqual(errors,[]);
 console.log('PASS: buy/sell demo orders, invalid quantities, cash/holdings checks, fractional units, review, history, JWT requests, exact order payload, duplicate prevention, ambiguous failure, sign-out, mobile layout.');
} finally {await browser.close();server.close()}
