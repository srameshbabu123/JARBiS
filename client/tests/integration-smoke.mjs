import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { chromium } from 'playwright';

const root = fileURLToPath(new URL('../dist/client/browser/', import.meta.url));
const server = createServer(async (req, res) => {
  try {
    const file = join(root, req.url === '/' ? 'index.html' : req.url.split('?')[0]);
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html' })[extname(file)] || 'application/octet-stream');
    res.end(await readFile(file));
  } catch { res.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, headless: true, args: ['--no-sandbox'] });
try {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const user = { id: -1001, fullName: 'Integration User', email: 'integration@example.test' };
  const token = ['test', Buffer.from(JSON.stringify({ exp: Math.floor(Date.now()/1000) + 3600 })).toString('base64url'), 'signature'].join('.');
  const account = { id: -1101, ownerId: user.id, balance: 0, accountType: 'INVESTMENT', currency: 'USD' };
  const orders = [];
  const assets = ['AAPL', 'AMZN', 'AMD'].map((name, index) => ({ id: -2001 - index, name, type: 'STOCK' }));
  let hasAccount = false, expire = false, posts = 0, failInitialization = false;
  const seen = new Set();
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    seen.add(`${req.method()} ${path}`);
    let body;
    if (path === '/api/auth/register' || path === '/api/auth/login') {
      assert.equal(req.headers().authorization, undefined);
      body = { user, token, tokenType: 'Bearer' };
    } else {
      assert.equal(req.headers().authorization, `Bearer ${token}`);
      if (expire) return route.fulfill({ status: 401, json: { message: 'Expired' } });
      if (path === '/api/auth/me' || path === '/api/users/-1001') {
        if (req.method() === 'PUT') Object.assign(user, req.postDataJSON());
        body = user;
      } else if (path === '/api/accounts/mine') body = hasAccount ? [account] : [];
      else if (path === '/api/accounts/mine/initialize' && req.method() === 'POST') {
        assert.deepEqual(req.postDataJSON(), {});
        if (failInitialization) return route.fulfill({ status: 503, json: { message: 'Account initialization unavailable' } });
        hasAccount = true; body = account;
      } else if (path === '/api/accounts/mine/-1101') body = account;
      else if (path === '/api/holdings/account/-1101') body = [];
      else if (path === '/api/orders/account/-1101') body = orders;
      else if (/\/accounts\/-1101\/(deposit|withdraw)$/.test(path)) {
        const amount = req.postDataJSON().amount;
        account.balance += path.endsWith('deposit') ? amount : -amount; body = account;
      } else if (path === '/api/assets') body = assets;
      else if (path.startsWith('/api/assets/by-symbol/')) {
        body = assets.find(asset => asset.name === path.split('/').at(-1));
        if (!body) return route.fulfill({ status: 404, json: { message: 'Unknown ticker' } });
      }
      else if (/^\/api\/market-data\/[^/]+\/latest$/.test(path)) {
        const symbol = path.split('/')[3];
        if (!assets.some(asset => asset.name === symbol)) return route.fulfill({ status: 404, json: { message: 'Unknown ticker' } });
        body = { symbol, price: 100, changePercent: 1 };
      }
      else if (path === '/api/market-data/AAPL/history') body = [{ symbol: 'AAPL', price: 99 }, { symbol: 'AAPL', price: 100 }];
      else if (path === '/api/orders' && req.method() === 'POST') {
        posts++; const data = req.postDataJSON();
        assert.deepEqual(data, { accountId: -1101, assetId: -2001, side: 'BUY', quantity: 2, price: 100 });
        body = { id: 1, ...data, asset: { id: -2001, name: 'AAPL' }, remainingQuantity: 2, status: 'PENDING' }; orders.push(body);
      } else throw Error('Unexpected API route: ' + req.method() + ' ' + path);
    }
    await route.fulfill({ json: body });
  });
  const origin = `http://127.0.0.1:${server.address().port}`;
  await page.goto(origin + '/');
  await page.waitForURL('**/#/login');
  await page.goto(origin + '/#/trade');
  await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
  await page.getByRole('button', { name: 'New here? Create an account' }).click();
  await page.getByLabel('Full name', { exact: true }).fill(user.fullName);
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(randomUUID());
  await page.getByRole('button', { name: 'Create account →', exact: true }).click();
  await page.getByText('Loaded account -1101 · USD').waitFor();
  assert.equal(await page.getByLabel('Cash amount').inputValue(), '');
  assert.equal(account.balance, 0);
  await page.getByText('No holdings in this account yet.').waitFor();
  for (const [action, button] of [['deposit', 'Review deposit'], ['withdraw', 'Review withdrawal']]) {
    await page.getByRole('button', { name: action === 'deposit' ? 'Deposit' : 'Withdraw', exact: true }).click();
    await page.getByLabel('Cash amount').fill(action === 'deposit' ? '1000' : '25');
    await page.getByRole('button', { name: button, exact: true }).click();
    await page.getByRole('button', { name: 'Confirm cash change', exact: true }).click();
    await page.getByRole('status').filter({ hasText: action === 'deposit' ? /Deposit completed/i : /Withdrawal completed/i }).waitFor();
  }
  await page.getByLabel('Full name', { exact: true }).fill('Updated User');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await page.getByText('Profile saved.', { exact: true }).waitFor();
  await page.reload();
  await page.getByRole('heading', { name: 'Personal details' }).waitFor();
  assert.equal(await page.getByLabel('Full name', { exact: true }).inputValue(), 'Updated User');
  for (const name of ['Overview', 'Charts', 'Watchlist', 'Screener', 'News', 'Order history', 'Trade']) {
    await page.locator('nav button').filter({ hasText: name }).click();
    await page.waitForURL('**/#/' + name.toLowerCase().replace(' ', '-'));
  }
  const ticker = page.getByRole('combobox', { name: 'Stock ticker symbol' });
  const suggestions = page.getByRole('listbox', { name: 'Available stock tickers' });
  await ticker.fill('a');
  await suggestions.waitFor();
  assert.deepEqual(await suggestions.getByRole('option').allTextContents().then(items => items.map(item => item.trim())), ['AAPL', 'AMD', 'AMZN']);
  await ticker.fill('aM');
  assert.deepEqual(await suggestions.getByRole('option').allTextContents().then(items => items.map(item => item.trim())), ['AMD', 'AMZN']);
  await suggestions.getByRole('option', { name: 'AMZN', exact: true }).click();
  assert.equal(await ticker.inputValue(), 'AMZN');
  assert.equal(await ticker.getAttribute('aria-expanded'), 'false');
  await ticker.fill('a');
  await ticker.press('ArrowUp');
  assert.equal(await ticker.getAttribute('aria-activedescendant'), 'trade-ticker-option-2');
  await ticker.press('ArrowDown');
  assert.equal(await ticker.getAttribute('aria-activedescendant'), 'trade-ticker-option-0');
  await ticker.press('Enter');
  assert.equal(await ticker.inputValue(), 'AAPL');
  assert.equal(await ticker.getAttribute('aria-expanded'), 'false');
  assert.equal(await page.locator('.review').count(), 0);
  await ticker.fill('a');
  await ticker.press('Escape');
  assert.equal(await ticker.getAttribute('aria-expanded'), 'false');
  await ticker.fill('zzzz');
  await page.getByRole('status').filter({ hasText: 'No matching tickers available.' }).waitFor();
  await ticker.fill('');
  assert.equal(await ticker.getAttribute('aria-expanded'), 'false');
  await ticker.fill('a');
  await ticker.press('Tab');
  assert.equal(await ticker.getAttribute('aria-expanded'), 'false');
  await ticker.fill('AAPL');
  assert.equal(await page.getByLabel('Quantity', { exact: true }).inputValue(), '');
  await page.getByLabel('Quantity', { exact: true }).fill('2');
  await page.getByRole('button', { name: 'Review buy order' }).click();
  await page.getByRole('button', { name: 'Submit order', exact: true }).dblclick();
  await page.locator('.receipt').waitFor(); assert.equal(posts, 1);
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
  assert.equal(await page.evaluate(() => localStorage.getItem('velocity-session')), null);
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(randomUUID());
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await page.getByText('Loaded account -1101 · USD').waitFor();
  expire = true;
  await page.locator('nav button').filter({ hasText: 'Trade' }).click();
  await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
  assert.equal(await page.evaluate(() => localStorage.getItem('velocity-session')), null);
  // An initialization failure must leave an unavailable account, never demo cash.
  expire = false; hasAccount = false; failInitialization = true;
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(randomUUID());
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await page.getByText('Account initialization unavailable', { exact: true }).waitFor();
  assert.equal(await page.locator('.hero-stat h2').innerText(), '—');
  assert.equal(await page.getByLabel('Cash amount').count(), 0);
  failInitialization = false;
  await page.getByRole('button', { name: 'Refresh accounts', exact: true }).click();
  await page.getByText('Loaded account -1101 · USD').waitFor();
  for (const endpoint of ['POST /api/auth/register', 'POST /api/auth/login', 'GET /api/auth/me', 'POST /api/accounts/mine/initialize', 'GET /api/accounts/mine/-1101', 'GET /api/holdings/account/-1101', 'GET /api/orders/account/-1101', 'POST /api/accounts/-1101/deposit', 'POST /api/accounts/-1101/withdraw', 'PUT /api/users/-1001', 'GET /api/assets', 'GET /api/assets/by-symbol/AAPL', 'GET /api/market-data/AAPL/latest', 'GET /api/market-data/AAPL/history', 'POST /api/orders']) assert(seen.has(endpoint), endpoint);
  assert.deepEqual(errors, []);
  console.log('PASS: authenticated UI routes, signup/account initialization, login, persisted session, profile, cash, quotes, history, ticker suggestions and keyboard selection, negative baseline IDs, order submission, sign-out and 401 redirect (mock API).');
} finally { await browser.close(); server.close(); }
