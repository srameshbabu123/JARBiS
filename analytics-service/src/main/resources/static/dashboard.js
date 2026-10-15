'use strict';
const $ = id => document.getElementById(id);
const state = {filter: null, report: null, metric: 'value', page: 0, total: 0, request: 0, tradesRequest: 0};
const integer = value => new Intl.NumberFormat('en-US').format(Number(value));
const dateString = date => date.toISOString().slice(0, 10);
const money = value => state.filter.currency === 'UNKNOWN' ? `${integer(value)} unknown currency` : new Intl.NumberFormat('en-US', {style:'currency',currency:state.filter.currency,maximumFractionDigits:2}).format(Number(value));
const compactMoney = value => state.filter.currency === 'UNKNOWN' ? integer(value) : new Intl.NumberFormat('en-US', {style:'currency',currency:state.filter.currency,notation:'compact',maximumFractionDigits:1}).format(Number(value));
async function api(path) {
  const response = await fetch(path, {cache:'no-store'});
  if (response.status === 401 || response.redirected) { location.assign('/login.html'); throw new Error('Your session has ended. Sign in again.'); }
  if (!response.ok) throw new Error(response.status === 400 ? 'Check the filters. Choose a date range of at most 367 days.' : 'Analytics is temporarily unavailable. Try refreshing in a moment.');
  return response.json();
}
function element(tag, text, className) {
  const el = document.createElement(tag); if (text !== undefined) el.textContent = text;
  if (className) el.className = className; return el;
}
function query() { return new URLSearchParams(Object.entries(state.filter).filter(([,v]) => v !== '' && v !== null)); }
function readFilter() {
  return {from:$('from').value,to:$('to').value,instrumentId:$('instrument').value,segment:$('segment').value,currency:$('currency').value,dataset:$('dataset').value};
}
function delta(id, current, previous) {
  const el = $(id); el.className = 'delta';
  if (!Number(previous)) { el.textContent = Number(current) ? 'New activity · no prior-period baseline' : 'No activity in either period'; return; }
  const change = (Number(current) - Number(previous)) / Number(previous) * 100;
  el.textContent = `${change >= 0 ? '↑' : '↓'} ${Math.abs(change).toFixed(1)}% vs preceding period`;
  if (change) el.classList.add(change > 0 ? 'up' : 'down');
}
function days(rows, key) {
  const points = new Map(rows.map(row => [row.day, Number(row[key])]));
  const output = [];
  const end = new Date(`${state.filter.to}T00:00:00Z`);
  for (let d = new Date(`${state.filter.from}T00:00:00Z`); d <= end; d.setUTCDate(d.getUTCDate()+1)) output.push({day:dateString(d),value:points.get(dateString(d)) || 0});
  return output;
}
function svgNode(tag, attrs, text) {
  const node = document.createElementNS('http://www.w3.org/2000/svg',tag);
  Object.entries(attrs || {}).forEach(([k,v]) => node.setAttribute(k,String(v)));
  if (text !== undefined) node.textContent = text; return node;
}
function chart(id, rows, key, currency = false) {
  const container = $(id); container.replaceChildren();
  if (!rows.length) { container.append(element('div','No completed trades in this period.','empty')); return; }
  const points = days(rows,key), width = 900, height = 215, left = 64, right = 14, top = 15, bottom = 30;
  const plotW = width-left-right, plotH = height-top-bottom, max = Math.max(1,...points.map(p=>p.value))*1.1;
  const svg = svgNode('svg',{viewBox:`0 0 ${width} ${height}`,preserveAspectRatio:'none'});
  const x = index => left + plotW*(points.length === 1 ? .5 : index/(points.length-1));
  const y = value => top+plotH*(1-value/max);
  for (let i=0;i<=4;i++) {
    const value = max*i/4, pos = y(value);
    svg.append(svgNode('line',{x1:left,x2:width-right,y1:pos,y2:pos,class:'grid'}));
    svg.append(svgNode('text',{x:left-10,y:pos+4,'text-anchor':'end',class:'axis'},currency?compactMoney(value):integer(value)));
  }
  const path = points.map((p,i)=>`${i?'L':'M'}${x(i)},${y(p.value)}`).join(' ');
  svg.append(svgNode('path',{d:`${path} L${x(points.length-1)},${y(0)} L${x(0)},${y(0)} Z`,class:'area'}));
  svg.append(svgNode('path',{d:path,class:'line'}));
  const stride = Math.max(1,Math.ceil(points.length/6));
  points.forEach((p,i)=>{
    const dot = svgNode('circle',{cx:x(i),cy:y(p.value),r:points.length>60?2:3,class:'point'});
    dot.append(svgNode('title',{},`${p.day}: ${currency?money(p.value):integer(p.value)}`)); svg.append(dot);
    if (i%stride===0 || i===points.length-1) {
      if(i===points.length-1 && i%stride && i%stride<stride/2) return;
      svg.append(svgNode('text',{x:x(i),y:height-6,'text-anchor':'middle',class:'axis'},new Date(`${p.day}T00:00:00Z`).toLocaleDateString('en-US',{month:'short',day:'numeric',timeZone:'UTC'})));
    }
  }); container.append(svg);
}
function rankings(rows) {
  const target = $('rankings'); target.replaceChildren();
  if (!rows.length) { target.append(element('p','No instruments traded in this period.','muted')); return; }
  const max = Number(rows[0].value) || 1;
  rows.forEach(row=>{
    const line = element('div',undefined,'ranking'), track=element('div',undefined,'track');
    // SVG supplies the proportional bar without inline styles (strict CSP).
    const svg=svgNode('svg',{width:'100%',height:'7',viewBox:'0 0 100 7',preserveAspectRatio:'none'});
    svg.append(svgNode('rect',{width:Math.max(1,Number(row.value)/max*100),height:7,rx:3,fill:'#c16de4'}));
    track.append(svg); line.append(element('span',row.name),track,element('strong',compactMoney(row.value))); target.append(line);
  });
}
function render(report) {
  const {summary:s,previous:p} = report;
  $('value').textContent=compactMoney(s.value); $('trades-count').textContent=integer(s.trades);
  $('clients-count').textContent=integer(s.clients); $('instruments-count').textContent=integer(s.instruments);
  [['value',s.value,p.value],['trades',s.trades,p.trades],['clients',s.clients,p.clients],['instruments',s.instruments,p.instruments]].forEach(([id,c,b])=>delta(`${id}-delta`,c,b));
  chart('activity-chart',report.activity,state.metric,state.metric==='value'); chart('clients-chart',report.clients,'clients'); rankings(report.instruments);
  $('activity-caption').textContent=state.metric==='value'?`Executed value in ${state.filter.currency} by day · UTC`:'Completed fills by day · UTC';
  $('period').textContent=`${state.filter.from} — ${state.filter.to} · ${state.filter.currency} · UTC`;
  $('notice').hidden=state.filter.dataset==='LIVE';
  $('notice').textContent='Legacy / sample dataset. These records predate the live event pipeline and are not evidence of current trading activity.';
  if(state.filter.currency==='UNKNOWN') { $('notice').hidden=false; $('notice').textContent='Currency could not be verified for these trades. Values are not comparable with a monetary reporting total.'; }
  const at=report.ingestion.last_ingested_at;
  $('freshness').textContent=at?`Latest live event ingested: ${new Date(at).toLocaleString('en-GB',{timeZone:'UTC'})} UTC`:'No live settlement events ingested yet';
}
async function loadTrades() {
  const request=++state.tradesRequest; const q=query(); q.set('page',state.page);
  const result=await api(`/admin/analytics/trades?${q}`); if(request!==state.tradesRequest) return;
  state.total=Number(result.total); const body=$('trade-rows'); body.replaceChildren();
  if(!result.rows.length) { const cell=element('td','No completed trades match these filters.'); cell.colSpan=7; const row=element('tr');row.append(cell);body.append(row); }
  result.rows.forEach(trade=>{
    const row=element('tr');
    [trade.id,trade.instrument,new Intl.NumberFormat('en-US',{maximumFractionDigits:8}).format(Number(trade.quantity)),money(trade.price),money(trade.value),new Date(trade.executed_at).toLocaleString('en-GB',{timeZone:'UTC'}),`${trade.buy_order_id} / ${trade.sell_order_id}`].forEach(value=>row.append(element('td',value)));
    body.append(row);
  });
  $('trade-total').textContent=`${integer(state.total)} fills`; $('page-label').textContent=`Page ${state.page+1} of ${Math.max(1,Math.ceil(state.total/25))}`;
  $('previous').disabled=state.page===0; $('next').disabled=(state.page+1)*25>=state.total;
}
async function load(reset = true) {
  if(!$('from').value || !$('to').value) { $('error').hidden=false;$('error').textContent='Choose both dates.';return; }
  const request=++state.request; state.tradesRequest++;
  if(reset) {state.filter=readFilter();state.page=0;}
  $('error').hidden=true;$('report').setAttribute('aria-busy','true');$('apply').disabled=true;
  try {
    const report=await api(`/admin/analytics/dashboard?${query()}`); if(request!==state.request) return;
    state.report=report;render(report);$('report').hidden=false;await loadTrades();
  } catch(e) { if(request===state.request) { $('error').hidden=false;$('error').textContent=e.message;$('report').hidden=true; } }
  finally {if(request===state.request){$('report').setAttribute('aria-busy','false');$('apply').disabled=false;}}
}
async function page(direction) {
  state.page+=direction;$('previous').disabled=true;$('next').disabled=true;
  try {await loadTrades();} catch(e){state.page-=direction;$('error').hidden=false;$('error').textContent=e.message;$('previous').disabled=state.page===0;$('next').disabled=(state.page+1)*25>=state.total;}
}
(async()=>{
  const now=new Date();$('to').value=dateString(now);now.setUTCDate(now.getUTCDate()-29);$('from').value=dateString(now);
  try {
    const options=await api('/admin/analytics/options');
    options.instruments.forEach(row=>$('instrument').append(new Option(row.name,row.id)));
    options.segments.forEach(row=>$('segment').append(new Option(row.name,row.name)));
    const currencies=new Set(['USD',...options.currencies.map(row=>row.name)]);$('currency').replaceChildren(...[...currencies].map(c=>new Option(c,c)));
    $('dataset').addEventListener('change',()=>{
      const dataset=options.datasets.find(d=>d.name===$('dataset').value);
      if(dataset && $('dataset').value==='LEGACY') {
        $('to').value=dataset.last_day;const start=new Date(`${dataset.last_day}T00:00:00Z`);start.setUTCDate(start.getUTCDate()-29);$('from').value=dateString(start);
      }
    });
    await load();
  } catch(e){$('error').hidden=false;$('error').textContent=e.message;$('report').setAttribute('aria-busy','false');}
})();
$('apply').addEventListener('click',()=>load());$('refresh').addEventListener('click',()=>load(false));
$('previous').addEventListener('click',()=>page(-1));$('next').addEventListener('click',()=>page(1));
document.querySelectorAll('[data-metric]').forEach(button=>button.addEventListener('click',()=>{
  state.metric=button.dataset.metric;document.querySelectorAll('[data-metric]').forEach(b=>{b.classList.toggle('selected',b===button);b.setAttribute('aria-pressed',String(b===button));});
  if(state.report) render(state.report);
}));
$('logout').addEventListener('click',async()=>{
  try { const csrf=await api('/session/csrf');const response=await fetch('/logout',{method:'POST',headers:{[csrf.headerName]:csrf.token}});if(!response.ok)throw new Error('Sign-out failed. Try again.');location.assign('/login.html'); }
  catch(e){$('error').hidden=false;$('error').textContent=e.message;}
});
