import test from 'node:test';
import assert from 'node:assert/strict';
import {Api,query,change,csv,escapeHtml} from '../client.mjs';
test('both independent segment filters reach the API',()=>{
 const p=new URLSearchParams(query({portfolioSize:'UNDER_10K',activityLevel:'ACTIVE',instrumentId:'',currency:'USD'}));
 assert.equal(p.get('portfolioSize'),'UNDER_10K');assert.equal(p.get('activityLevel'),'ACTIVE');assert.equal(p.has('instrumentId'),false);
});
test('no invented growth when previous baseline is zero',()=>{assert.equal(change(2,0),'No previous baseline');assert.equal(change(0,0),'No change');assert.match(change(12,10),/20.0%/);});
test('untrusted evidence and CSV formula text are escaped',()=>{assert.equal(escapeHtml('<script>"&'), '&lt;script&gt;&quot;&amp;');assert.match(csv([{instrument:'=1+1',quantity:2}]),/"'=1\+1"/);});
test('login retains cookies and rotates CSRF before mutations',async()=>{
 const calls=[],responses=[{token:'anonymous'},null,{token:'authenticated'},{username:'david',roles:['ROLE_OPERATIONS']},{}];
 const api=new Api(async(path,options)=>{calls.push({path,...options});const data=responses.shift();return {ok:true,status:data===null?204:200,json:async()=>data};});
 const user=await api.login('david','test');assert.equal(user.username,'david');
 await api.request('/admin/disputes',{method:'POST',body:{orderId:2}});
 assert.equal(calls[1].headers['X-CSRF-TOKEN'],'anonymous');
 assert.equal(calls[4].headers['X-CSRF-TOKEN'],'authenticated');
 assert.ok(calls.every(c=>c.credentials==='same-origin'));
});
test('conflict errors remain visible for versioned case updates',async()=>{
 const api=new Api(async()=>({ok:false,status:409,json:async()=>({detail:'Dispute changed; reload before updating'})}));
 await assert.rejects(api.request('/admin/disputes/id',{method:'PATCH',body:{expectedVersion:1}}),e=>e.status===409&&e.message.includes('reload'));
});

// A failed module load must never fall back to putting credentials in a GET URL.
test('login defaults to POST and waits for JavaScript initialization',async()=>{
 const {readFile}=await import('node:fs/promises');
 const html=await readFile(new URL('../index.html',import.meta.url),'utf8');
 assert.match(html,/<form id="login-form"[^>]*method="post"[^>]*action="\/login"/);
 assert.match(html,/<button id="sign-in"[^>]*disabled/);
 const app=await readFile(new URL('../app.js',import.meta.url),'utf8');
 assert.match(app,/from '\.\/client\.js'/);
});

test('date validation explains oversized historical ranges before a request',()=>{
 assert.doesNotThrow(()=>validateDates('2026-01-01','2026-12-31'));
 assert.throws(()=>validateDates('2024-03-01','2026-10-16'),/367 days/);
 assert.throws(()=>validateDates('2026-10-16','2026-10-01'),/on or after/);
});

test('dashboard does not ask users to distinguish OLAP data sources',async()=>{
 const {readFile}=await import('node:fs/promises');
 const html=await readFile(new URL('../index.html',import.meta.url),'utf8');
 assert.doesNotMatch(html,/name="dataset"/);
 const app=await readFile(new URL('../app.js',import.meta.url),'utf8');
 assert.doesNotMatch(app,/elements\.dataset/);
});
