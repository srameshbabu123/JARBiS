import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, readdir } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const root = fileURLToPath(new URL('../dist/client/browser/', import.meta.url));
let screenerChunk;
for (const file of await readdir(root)) {
  if (file.endsWith('.js') && (await readFile(join(root, file), 'utf8')).includes('app-screener-page')) {
    screenerChunk = file;
  }
}
assert.ok(screenerChunk, 'Built screener chunk must exist');
let failures = 0;
let documents = 0;
const server = createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (path === '/') documents++;
    if (path === '/' + screenerChunk && failures++ === 0) {
      res.writeHead(404).end();
      return;
    }
    const file = join(root, path === '/' ? 'index.html' : path);
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html' })[extname(file)] || 'application/octet-stream');
    res.end(await readFile(file));
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
let browser;
try {
  browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined, headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage();
  const user = { id: -1, fullName: 'Recovery Test', email: 'recovery@example.test' };
  const token = ['test', Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url'), 'signature'].join('.');
  await page.addInitScript(({ user, token }) => {
    localStorage.setItem('velocity-session', JSON.stringify({ user, token }));
  }, { user, token });
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ json: path === '/api/auth/me' ? user : [] });
  });
  await page.goto(`http://127.0.0.1:${server.address().port}/#/screener`);
  await page.getByRole('heading', { name: 'Market screener', exact: true }).waitFor();
  assert.equal(failures, 2, 'Failed module is requested again after reload');
  assert.equal(documents, 2, 'Exactly one automatic reload');
  assert.ok(page.url().endsWith('#/screener'), 'Requested route is preserved');
  console.log('PASS: Screener recovers from a missing lazy bundle with one reload.');
} finally {
  await browser?.close();
  await new Promise(resolve => server.close(resolve));
}