import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';

const seen = [];
const backend = createServer(async (req, res) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  seen.push({ path: req.url, method: req.method, authorization: req.headers.authorization, body: Buffer.concat(chunks).toString() });
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ status: 'UP' }));
});
await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve));
const portServer = createServer();
await new Promise(resolve => portServer.listen(0, '127.0.0.1', resolve));
const port = portServer.address().port;
await new Promise(resolve => portServer.close(resolve));
const child = spawn(process.execPath, ['node_modules/@angular/cli/bin/ng.js', 'serve', '--host', '127.0.0.1', '--port', String(port)], {
  env: { ...process.env, BACKEND_URL: `http://127.0.0.1:${backend.address().port}` },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let output = '';
child.stdout.on('data', data => output += data);
child.stderr.on('data', data => output += data);
try {
  const deadline = Date.now() + 90000;
  let response;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw Error(output);
    try { response = await fetch(`http://127.0.0.1:${port}/api/actuator/health`); if (response.ok) break; } catch {}
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  assert(response?.ok, output);
  const paths = ['/auth/login', '/auth/register', '/auth/me', '/accounts/mine', '/accounts/mine/1', '/users/1', '/holdings/account/1', '/orders/account/1', '/assets', '/assets/by-symbol/AAPL', '/market-data/AAPL/latest', '/market-data/AAPL/history?from=2026-10-01T00%3A00%3A00Z&to=2026-10-20T00%3A00%3A00Z'];
  for (const path of paths) {
    assert.equal((await fetch(`http://127.0.0.1:${port}/api${path}`, { headers: { Authorization: 'Bearer proxy-test' } })).status, 200);
    assert.equal(seen.at(-1).path, path);
    assert.equal(seen.at(-1).authorization, 'Bearer proxy-test');
  }
  await fetch(`http://127.0.0.1:${port}/api/orders`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer proxy-test' }, body: JSON.stringify({ accountId: 1 }) });
  assert.deepEqual(seen.at(-1), { path: '/orders', method: 'POST', authorization: 'Bearer proxy-test', body: '{"accountId":1}' });
  console.log('PASS: real Angular dev server forwards nested /api routes, query strings, POST bodies and JWT headers to BACKEND_URL.');
} finally {
  child.kill('SIGKILL');
  child.stdout.destroy();
  child.stderr.destroy();
  child.unref();
  backend.closeAllConnections();
  backend.close();
}
