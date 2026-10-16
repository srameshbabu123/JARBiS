export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
export function query(values) {
  return new URLSearchParams(Object.entries(values).filter(([,v]) => v !== '' && v != null)).toString();
}
export function change(current, previous) {
  const a=Number(current), b=Number(previous);
  return b === 0 ? (a === 0 ? 'No change' : 'No previous baseline') : `${a>=b?'↑':'↓'} ${Math.abs((a-b)/b*100).toFixed(1)}% vs previous period`;
}
export function csv(rows) {
  if (!rows.length) return '';
  const keys=Object.keys(rows[0]);
  const cell=value => {
    let text=String(value??'');
    if (/^[=+@\-\t\r]/.test(text)) text="'"+text;
    return '"'+text.replaceAll('"','""')+'"';
  };
  return [keys,...rows.map(r=>keys.map(k=>r[k]))].map(r=>r.map(cell).join(',')).join('\r\n');
}
export class Api {
  constructor(fetcher=globalThis.fetch.bind(globalThis)) {this.fetcher=fetcher;this.csrf='';}
  async request(path, {method='GET', body, form=false}={}) {
    const headers={};
    if(method!=='GET') headers['X-CSRF-TOKEN']=this.csrf;
    if(body!==undefined) headers['Content-Type']=form?'application/x-www-form-urlencoded':'application/json';
    const response=await this.fetcher(path,{method,headers,credentials:'same-origin',body:body===undefined?undefined:form?new URLSearchParams(body):JSON.stringify(body)});
    if(!response.ok) {
      let message=`Request failed (${response.status})`;
      try {const data=await response.json(); if(typeof data.detail==='string') message=data.detail;} catch {}
      const error=new Error(message);error.status=response.status;throw error;
    }
    return response.status===204 ? null : response.json();
  }
  async token() {this.csrf=(await this.request('/session/csrf')).token;}
  async login(username,password) {await this.token();await this.request('/login',{method:'POST',form:true,body:{username,password}});await this.token();return this.request('/session/me');}
}
