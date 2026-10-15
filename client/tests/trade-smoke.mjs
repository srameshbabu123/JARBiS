import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { chromium } from 'playwright';

// Serve the production build; all /api requests below are intercepted in the browser.
const root=new URL('../dist/client/browser/',import.meta.url).pathname;
const server=createServer(async(req,res)=>{
 try {const file=join(root,req.url==='/'?'index.html':req.url.split('?')[0]);res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'application/octet-stream');res.end(await readFile(file))}
 catch {res.writeHead(404).end()}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
await mkdir(new URL('../test-results/',import.meta.url),{recursive:true});
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--no-zygote','--single-process','--disable-gpu']});
try {
 const page=await browser.newPage({viewport:{width:1440,height:1100}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const origin=`http://127.0.0.1:${server.address().port}`;
 await page.goto(origin+'/#trade');
 await page.getByRole('heading',{name:'Order ticket'}).waitFor();
 assert.match(await page.title(),/Velocity/);
 await page.waitForFunction(()=>[...document.querySelectorAll('app-brand-logo img')].length===2 && [...document.querySelectorAll('app-brand-logo img')].every(img=>img.complete && img.naturalWidth>0));
 assert.equal(await page.getByText('JARBiS',{exact:false}).count(),0);
 await page.screenshot({path:new URL('../test-results/trade.png',import.meta.url).pathname,fullPage:true});
 // Exact palettes, theme-specific artwork, and saved mode across a reload.
 const palette=()=>page.evaluate(()=>{const style=getComputedStyle(document.documentElement);return Object.fromEntries(['bg','chart-grid','orange','pink','magenta','purple'].map(key=>[key,style.getPropertyValue('--'+key).trim().toLowerCase()]))});
 assert.deepEqual(await palette(),{bg:'#050505','chart-grid':'#262626',orange:'#ff6b00',pink:'#ff4d8d',magenta:'#d93cbe',purple:'#9d2bff'});
 await page.getByRole('button',{name:'Switch to light mode',exact:true}).click();
 await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');
 assert.deepEqual(await palette(),{bg:'#f9f9f9','chart-grid':'#d1d1d1',orange:'#ff5500',pink:'#e60067',magenta:'#b812a4',purple:'#7209b7'});
 await page.waitForFunction(()=>[...document.querySelectorAll('app-brand-logo img')].every(img=>img.src.endsWith('velocity-logo-light.jpg') && img.complete && img.naturalWidth>0));
 assert.equal(await page.evaluate(()=>getComputedStyle(document.body).backgroundColor),'rgb(249, 249, 249)');
 await page.evaluate(()=>Promise.all(document.getAnimations().map(animation=>animation.finished.catch(()=>{}))));
 await page.screenshot({path:new URL('../test-results/trade-light.png',import.meta.url).pathname,fullPage:true});
 await page.reload();await page.getByRole('heading',{name:'Order ticket'}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'light');
 await page.getByRole('button',{name:'Switch to dark mode',exact:true}).click();
 await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');
 const quantity=page.getByLabel('Quantity',{exact:true});
 await quantity.fill('0');assert(await page.getByRole('button',{name:'Review buy order'}).isDisabled());
 await quantity.fill('10000');assert(await page.getByRole('button',{name:'Review buy order'}).isDisabled());
 await quantity.fill('2.5');await page.getByRole('button',{name:'Review buy order'}).click();
 await page.getByRole('button',{name:'Place demo order'}).waitFor();
 assert(await quantity.isDisabled());
 await page.getByRole('button',{name:'Place demo order'}).click();
 await page.locator('.receipt').waitFor();
 assert.match(await page.locator('.receipt').innerText(),/PENDING/);
 await page.getByRole('button',{name:'New order',exact:true}).click();
 await page.getByRole('button',{name:'↘ Sell',exact:true}).click();
 await quantity.fill('81');assert(await page.getByRole('button',{name:'Review sell order'}).isDisabled());
 await quantity.fill('5');await page.getByRole('button',{name:'Review sell order'}).click();await page.getByRole('button',{name:'Place demo order'}).click();
 await page.getByText('75 units',{exact:true}).waitFor();
 assert.match(await page.locator('.account-panel').innerText(),/75 units/);
 await page.getByRole('button',{name:'View order history ↗'}).click();
 assert.match(await page.locator('tbody tr').first().innerText(),/SELL/);
 // Feature routes, shared market state, and browser history survive lazy page transitions.
 await page.locator('nav button').filter({hasText:'Screener'}).click();
 await page.getByRole('heading',{name:'Market screener',exact:true}).waitFor();
 await page.getByLabel('Search stocks').fill('Apple');
 assert.equal(await page.locator('tbody tr').count(),1);
 await page.getByRole('button',{name:'Remove AAPL',exact:true}).click();
 await page.getByRole('button',{name:'Add AAPL',exact:true}).waitFor();
 await page.locator('nav button').filter({hasText:'Watchlist'}).click();
 await page.getByRole('heading',{name:'My watchlist',exact:true}).waitFor();
 assert.equal(await page.locator('tbody tr').count(),0);
 await page.getByLabel('Search stocks').fill('');
 await page.getByRole('button',{name:'Remove NVDA',exact:true}).waitFor();
 assert.equal(await page.locator('tbody tr').count(),3);
 await page.locator('nav button').filter({hasText:'Charts'}).click();
 await page.getByRole('heading',{name:'NVIDIA · NVDA'}).waitFor();
 await page.goBack();
 await page.getByRole('heading',{name:'My watchlist',exact:true}).waitFor();
 for(const [route,heading] of [['Overview','Your holdings'],['News','Market reading'],['Profile','Personal details']]){
  await page.locator('nav button').filter({hasText:route}).click();
  await page.getByRole('heading',{name:heading}).waitFor();
 }
 await page.reload();
 await page.getByRole('heading',{name:'Personal details'}).waitFor();
 await page.locator('nav button').filter({hasText:'Watchlist'}).click();
 await page.getByRole('heading',{name:'My watchlist',exact:true}).waitFor();
 assert.equal(await page.locator('tbody tr').count(),3);
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
   else if(path==='/api/accounts/mine')body=[{id:42,ownerId:9,balance:10000,accountType:'INVESTMENT',currency:'USD'}];
   else if(path==='/api/accounts/mine/42')body={id:42,ownerId:9,balance:10000,accountType:'BROKERAGE',currency:'USD'};
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
 await page.getByText('Loaded account 42 · USD').waitFor();
 assert.equal(await page.getByLabel('User ID',{exact:true}).count(),0);
 assert.equal(await page.getByLabel('Account ID',{exact:true}).count(),0);
 await page.locator('nav button').filter({hasText:'Trade'}).click();
 await page.getByRole('heading',{name:'Order ticket',exact:true}).waitFor();
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
 await page.getByRole('heading',{name:'Order ticket',exact:true}).waitFor();
 await page.getByText('Demo order',{exact:true}).waitFor();
 assert.match(await page.locator('.mode').innerText(),/Demo/);
 await page.setViewportSize({width:390,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:new URL('../test-results/trade-mobile.png',import.meta.url).pathname,fullPage:true});
 assert.deepEqual(errors,[]);
 console.log('PASS: buy/sell demo orders, invalid quantities, cash/holdings checks, fractional units, review, history, JWT requests, exact order payload, duplicate prevention, ambiguous failure, sign-out, mobile layout.');
} finally {await browser.close();server.close()}
